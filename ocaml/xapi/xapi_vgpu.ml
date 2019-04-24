(*
 * Copyright (C) 2006-2011 Citrix Systems Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; version 2.1 only. with the special
 * exception on linking described in file LICENSE.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *)
module D=Debug.Make(struct let name="xapi" end)
open D

(* Mutex to prevent duplicate VGPUs being created by accident *)
let m = Mutex.create ()

(* 1. Since multiple vgpus support, only device number = 0 or specify [11,31] since demu restriction.
   2. device = 0 will requires xapi to give a valid number
    - First sort the device number and the trying to find the next valid from range *)

let range low high =
  let rec aux low high =
    if low > high then [] else low :: aux (low+1) high in
  aux low high

let all_valid_devices = range 11 31

let get_valid_device ~__context ~device ~vM =
  let d = int_of_string device in
  if d >= 11 && d <= 31 then device
  else if d = 0 then begin
    let all = Db.VM.get_VGPUs ~__context ~self:vM in
    let all_devices = List.map (fun self -> Db.VGPU.get_device ~__context ~self |> int_of_string) all in
    let sorted = List.sort compare all_devices in
    match sorted with
    | [] -> "11"
    | _ ->
      try
        let valid = List.find (fun d -> not (List.mem d sorted)) all_valid_devices in
        string_of_int valid
      with Not_found -> raise (Api_errors.Server_error (Api_errors.invalid_device, [device]))
  end else raise (Api_errors.Server_error (Api_errors.invalid_device, [device]))

let create' ~__context  ~vM ~gPU_group ~device ~other_config ~_type ~powerstate_check =
  let vgpu = Ref.make () in
  let uuid = Uuid.to_string (Uuid.make_uuid ()) in
  if not (Pool_features.is_enabled ~__context Features.GPU) then
    raise (Api_errors.Server_error (Api_errors.feature_restricted, []));
  if powerstate_check then
    Xapi_vm_lifecycle.assert_initial_power_state_is ~__context ~self:vM ~expected:`Halted;
  let device_id =
    try
      get_valid_device ~__context ~device ~vM
    with e -> raise e
  in
  (* For backwards compatibility, convert Ref.null into the passthrough type. *)
  let _type =
    if _type = Ref.null
    then Xapi_vgpu_type.find_or_create ~__context Xapi_vgpu_type.passthrough_gpu
    else begin
      if Db.is_valid_ref __context _type
      then _type
      else raise (Api_errors.Server_error
                    (Api_errors.invalid_value, ["type"; Ref.string_of _type]))
    end
  in
  (* during multiple vgpus creation:
    1. Underlying vgpu_type should support multiple
    2. _type must be listed on the all vgpu_type's compatible lists*)
  let existing = Db.VM.get_VGPUs ~__context ~self:vM in
  if existing <> [] then begin
    let types = List.map (fun vgpu -> Db.VGPU.get_type ~__context ~self:vgpu) existing in
    let is_in_compatible_lists _type vgpu_type =
      let compatible_lists = Db.VGPU_type.get_compatible_types_in_vm ~__context ~self:vgpu_type in
      List.mem _type compatible_lists
    in
    if not (List.for_all (fun vgpu_type -> is_in_compatible_lists _type vgpu_type) types) then
      raise (Api_errors.Server_error (Api_errors.vgpu_type_not_compatible, [Ref.string_of _type]))
  end;


  Stdext.Threadext.Mutex.execute m (fun () ->
      Db.VGPU.create ~__context ~ref:vgpu ~uuid ~vM ~gPU_group ~device:device_id
        ~currently_attached:false ~other_config ~_type ~resident_on:Ref.null
        ~scheduled_to_be_resident_on:Ref.null
        ~compatibility_metadata:[]
        ;
    );
  debug "VGPU ref='%s' created (VM = '%s', type = '%s')" (Ref.string_of vgpu) (Ref.string_of vM) (Ref.string_of _type);
  vgpu

(* - create is defined by the autogenerated code, so we keep the same signature for it but add
   a new function create' that will accept extra parameters indicating the desired behaviour.
   - create may be called during VM.import(eg. VM cross pool migration with checkpoints), no need
   to constraint the power state in this case.
 *)
let create ~__context  ~vM ~gPU_group ~device ~other_config ~_type =
  let powerstate_check = not (Db.VM.get_is_a_snapshot ~__context ~self:vM) in
  create' ~__context  ~vM ~gPU_group ~device ~other_config ~_type ~powerstate_check

let destroy ~__context ~self =
  let vm = Db.VGPU.get_VM ~__context ~self in
  if Helpers.is_running ~__context ~self:vm then
    raise (Api_errors.Server_error (Api_errors.operation_not_allowed, ["vGPU currently attached to a running VM"]));
  Db.VGPU.destroy ~__context ~self

let atomic_set_resident_on ~__context ~self ~value = assert false

let copy ~__context ~vm vgpu =
  let all = Db.VGPU.get_record ~__context ~self:vgpu in
  let vgpu = create' ~__context
      ~device:all.API.vGPU_device
      ~gPU_group:all.API.vGPU_GPU_group
      ~vM:vm
      ~other_config:all.API.vGPU_other_config
      ~_type:all.API.vGPU_type
      ~powerstate_check:false
  in
  if all.API.vGPU_currently_attached then
    Db.VGPU.set_currently_attached ~__context ~self:vgpu ~value:true;
  vgpu

let requires_passthrough ~__context ~self =
  let _type = Db.VGPU.get_type ~__context ~self in
  Xapi_vgpu_type.requires_passthrough ~__context ~self:_type
