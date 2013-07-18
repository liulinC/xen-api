
open Stringext
open Opt

module PCI_DB = struct
	type class_id = string
	type subclass_id = string
	type vendor_id = string
	type subvendor_id = string
	type device_id = string
	type subdevice_id = string

	type pci_class = {
		c_name : string;
		subclass_names : (subclass_id, string) Hashtbl.t
	}

	type device = {
		d_name : string;
		subdevice_names : ((subvendor_id * subdevice_id), string) Hashtbl.t
	}

	type vendor = {
		v_name : string;
		devices : (device_id, device) Hashtbl.t
	}

	type t = {
		classes : (class_id, pci_class) Hashtbl.t;
		vendors : (vendor_id, vendor) Hashtbl.t
	}

	let make c_size v_size =
		{classes = Hashtbl.create c_size; vendors = Hashtbl.create v_size}

	let add_class t id c = Hashtbl.add t.classes id c

	let add_subclass t c_id sc_id name =
		let c = Hashtbl.find t.classes c_id in
		Hashtbl.add c.subclass_names sc_id name

	let add_vendor t v_id v = Hashtbl.add t.vendors v_id v

	let add_device t v_id d_id d =
		let v = Hashtbl.find t.vendors v_id in
		Hashtbl.add v.devices d_id d

	let add_subdevice t v_id d_id sv_id sd_id name =
		let v = Hashtbl.find t.vendors v_id in
		let d = Hashtbl.find v.devices d_id in
		Hashtbl.add d.subdevice_names (sv_id, sd_id) name
end

open PCI_DB

let parse_file path =
	let lines = Unixext.read_lines ~path in
	let lines =
		List.filter (fun s -> (not (String.startswith "#" s || s = ""))) lines in
	let strip = String.strip String.isspace in
	let cur_class, cur_vendor, cur_device = ref None, ref None, ref None in
	let parse_one_line line pci_db =
		match String.explode (String.sub line 0 2) with
		| 'C' :: _ -> (* this is a class definition *)
			cur_vendor := None; cur_device := None;
			let line = String.sub_to_end line 2 in
			let [id; name] = String.split ' ' line ~limit:2 in
			let pci_class =
				{c_name = strip name; subclass_names = Hashtbl.create 8} in
			add_class pci_db id pci_class;
			cur_class := Some id
		| ['\t'; '\t'] ->
			let line = String.sub_to_end line 2 in
			begin match !cur_device with
			| Some d_id -> (* this is a subdevice definition *)
				let [sv_id; sd_id; name] = String.split ' ' line ~limit:3 in
				add_subdevice pci_db (Opt.unbox !cur_vendor) d_id sv_id sd_id (strip name)
			| None -> ()
			end
		| '\t' :: _ ->
			let line = String.sub_to_end line 1 in
			begin match !cur_vendor with
			| Some v -> (* this is a device definition *)
				let [d_id; name] = String.split ' ' line ~limit:2 in
				let device =
					{d_name = strip name; subdevice_names = Hashtbl.create 0} in
				add_device pci_db (Opt.unbox !cur_vendor) d_id device;
				cur_device := Some d_id
			| None -> (* this is a subclass definition *)
				let [sc_id; name] = String.split ' ' line ~limit:2 in
				add_subclass pci_db (Opt.unbox !cur_class) sc_id (strip name)
			end
		| _ -> (* this is a vendor definition *)
			cur_class := None; cur_device := None;
			let [v_id; name] = String.split ' ' line ~limit:2 in
			let vendor =
				{v_name = strip name; devices = Hashtbl.create 8} in
			add_vendor pci_db v_id vendor;
			cur_vendor := Some v_id
	in
	let rec parse_lines lines pci_db =
		match lines with
		| [] -> pci_db
		| line :: remaining ->
			parse_one_line line pci_db;
			parse_lines remaining pci_db
	in
	
	let pci_db = PCI_DB.make 24 2048 in
	parse_lines lines pci_db

let () =
	try
		ignore (parse_file "/usr/share/hwdata/pci.ids")
	with e ->
		failwith "Failed to parse pci database"
