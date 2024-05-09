/*
 * Copyright (c) Cloud Software Group, Inc.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 *   1) Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 * 
 *   2) Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials
 *      provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

using System;
using System.Collections;
using System.Collections.Generic;
using System.IO;
using System.Management.Automation;
using System.Reflection;
using System.Xml;

using XenAPI;

namespace Citrix.XenServer
{
    class CommonCmdletFunctions
    {
        private const string SessionsVariable = "global:Citrix.XenServer.Sessions";

        private const string DefaultSessionVariable = "global:XenServer_Default_Session";

        private const string KnownServerCertificatesFilePathVariable = "global:KnownServerCertificatesFilePath";

        static CommonCmdletFunctions()
        {
            Session.UserAgent = string.Format("XenServerPSModule/{0}", Assembly.GetExecutingAssembly().GetName().Version);
        }

        internal static Dictionary<string, Session> GetAllSessions(PSCmdlet cmdlet)
        {
            object obj = cmdlet.SessionState.PSVariable.GetValue(SessionsVariable);
            return obj as Dictionary<string, Session> ?? new Dictionary<string, Session>();
        }

        /// <summary>
        /// Save the session dictionary as a PowerShell variable. It includes the
        /// PSCredential so, in case the session times out, any cmdlet can try to
        /// remake it
        /// </summary>
        internal static void SetAllSessions(PSCmdlet cmdlet, Dictionary<string, Session> sessions)
        {
            cmdlet.SessionState.PSVariable.Set(SessionsVariable, sessions);
        }

        internal static Session GetDefaultXenSession(PSCmdlet cmdlet)
        {
            return cmdlet.SessionState.PSVariable.GetValue(DefaultSessionVariable) as Session;
        }

        internal static void SetDefaultXenSession(PSCmdlet cmdlet, Session session)
        {
            cmdlet.SessionState.PSVariable.Set(DefaultSessionVariable, session);
        }

        internal static string GetKnownServerCertificatesFilePathVariable(PSCmdlet cmdlet)
        {
            var knownCertificatesFilePathObject = cmdlet.SessionState.PSVariable.GetValue(KnownServerCertificatesFilePathVariable);
            if (knownCertificatesFilePathObject is PSObject psObject)
                return psObject.BaseObject as string;
            return knownCertificatesFilePathObject?.ToString() ?? string.Empty;
        }

        internal static string GetUrl(string hostname, int port)
        {
            return string.Format("{0}://{1}:{2}", port == 80 ? "http" : "https", hostname, port);
        }

        public static Dictionary<string, string> LoadCertificates(PSCmdlet cmdlet)
        {
            Dictionary<string, string> certificates = new Dictionary<string, string>();
            var knownServerCertificatesFilePath = GetKnownServerCertificatesFilePathVariable(cmdlet);

            if (File.Exists(knownServerCertificatesFilePath))
            {
                XmlDocument doc = new XmlDocument();
                doc.Load(knownServerCertificatesFilePath);

                foreach (XmlNode node in doc.GetElementsByTagName("certificate"))
                {
                    XmlAttribute hostAtt = node.Attributes?["hostname"];
                    XmlAttribute fngprtAtt = node.Attributes?["fingerprint"];

                    if (hostAtt != null && fngprtAtt != null)
                        certificates[hostAtt.Value] = fngprtAtt.Value;
                }
            }

            return certificates;
        }

        public static void SaveCertificates(PSCmdlet cmdlet, Dictionary<string, string> certificates)
        {
            var knownServerCertificatesFilePath = GetKnownServerCertificatesFilePathVariable(cmdlet);
            string dirName = Path.GetDirectoryName(knownServerCertificatesFilePath);

            if (!Directory.Exists(dirName))
                Directory.CreateDirectory(dirName);

            XmlDocument doc = new XmlDocument();
            XmlDeclaration decl = doc.CreateXmlDeclaration("1.0", "utf-8", null);
            doc.AppendChild(decl);
            XmlNode node = doc.CreateElement("certificates");

            foreach (KeyValuePair<string, string> cert in certificates)
            {
                XmlNode certNode = doc.CreateElement("certificate");
                XmlAttribute hostname = doc.CreateAttribute("hostname");
                XmlAttribute fingerprint = doc.CreateAttribute("fingerprint");
                hostname.Value = cert.Key;
                fingerprint.Value = cert.Value;
                certNode.Attributes?.Append(hostname);
                certNode.Attributes?.Append(fingerprint);
                node.AppendChild(certNode);
            }

            doc.AppendChild(node);
            doc.Save(knownServerCertificatesFilePath);
        }

        public static string FingerprintPrettyString(string fingerprint)
        {
            List<string> pairs = new List<string>();
            while (fingerprint.Length > 1)
            {
                pairs.Add(fingerprint.Substring(0, 2));
                fingerprint = fingerprint.Substring(2);
            }

            if (fingerprint.Length > 0)
                pairs.Add(fingerprint);
            return string.Join(":", pairs.ToArray());
        }

        public static Dictionary<T, S> ConvertHashTableToDictionary<T, S>(Hashtable tbl)
        {
            if (tbl == null)
                return null;

            var dict = new Dictionary<T, S>();
            foreach (DictionaryEntry entry in tbl)
                dict.Add((T)entry.Key, (S)entry.Value);

            return dict;
        }

        public static Hashtable ConvertDictionaryToHashtable<T, S>(Dictionary<T, S> dict)
        {
            if (dict == null)
                return null;

            var tbl = new Hashtable();
            foreach (KeyValuePair<T, S> pair in dict)
                tbl.Add(pair.Key, pair.Value);

            return tbl;
        }

        internal static object EnumParseDefault(Type t, string s)
        {
            try
            {
                if (s == null)
                    s = string.Empty;

                return Enum.Parse(t, s.Replace('-', '_'));
            }
            catch (ArgumentException)
            {
                try
                {
                    return Enum.Parse(t, "unknown");
                }
                catch (ArgumentException)
                {
                    try
                    {
                        return Enum.Parse(t, "Unknown");
                    }
                    catch (ArgumentException)
                    {
                        return 0;
                    }
                }
            }
        }
    }
}
