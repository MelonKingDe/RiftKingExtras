// Volume of the Spotify app (Windows Core Audio session). Taken from the CC0 SpotifyOverlayHelper.
using System;
using System.Runtime.InteropServices;

namespace RiftKingMusicHelper
{
    internal static class VolumeControl
    {
        private const string TargetProcessName = "spotify";

        public static float? Get()
        {
            var volume = FindSpotifyVolumeInterface();
            if (volume == null) return null;

            volume.GetMasterVolume(out float level);
            return level;
        }

        public static float? Adjust(float delta)
        {
            var volume = FindSpotifyVolumeInterface();
            if (volume == null) return null;

            volume.GetMasterVolume(out float level);
            float newLevel = Math.Clamp(level + delta, 0f, 1f);
            volume.SetMasterVolume(Math.Clamp(newLevel,0f,1f), Guid.Empty);
            volume.SetMute(false, Guid.Empty);
            return newLevel;
        }

        private static ISimpleAudioVolume FindSpotifyVolumeInterface()
        {
            var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumerator();
            enumerator.GetDefaultAudioEndpoint(0 /* eRender */, 1 /* eMultimedia */, out IMMDevice device);

            Guid iid = typeof(IAudioSessionManager2).GUID;
            device.Activate(ref iid, 0x17 /* CLSCTX_ALL */, IntPtr.Zero, out object o);
            var sessionManager = (IAudioSessionManager2)o;

            sessionManager.GetSessionEnumerator(out IAudioSessionEnumerator sessionEnumerator);
            sessionEnumerator.GetCount(out int count);

            for (int i = 0; i < count; i++)
            {
                sessionEnumerator.GetSession(i, out IAudioSessionControl control);
                var control2 = (IAudioSessionControl2)control;

                try
                {
                    control2.GetProcessId(out uint pid);
                    var proc = System.Diagnostics.Process.GetProcessById((int)pid);

                    if (proc.ProcessName.Contains(
                        "spotify",
                        StringComparison.OrdinalIgnoreCase))
                    {
                        return (ISimpleAudioVolume)control;
                    }
                }
                catch
                {
                    // Session's process may have exited between enumeration and lookup, or
                    // GetProcessId can fail for system sound sessions — skip and keep looking.
                }
            }

            return null;
        }

        // ---- Minimal Core Audio COM interop surface ----
        // Only the methods we actually call are implemented; everything before them
        // in each interface's real vtable is a placeholder so the method order lines
        // up correctly.

        [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
        private class MMDeviceEnumerator { }

        [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IMMDeviceEnumerator
        {
            int NotImpl1(); // EnumAudioEndpoints
            int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
        }

        [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IMMDevice
        {
            int Activate(ref Guid iid, int clsCtx, IntPtr activationParams,
                [MarshalAs(UnmanagedType.IUnknown)] out object ppInterface);
        }

        [ComImport, Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IAudioSessionManager2
        {
            int NotImpl1(); // GetAudioSessionControl
            int NotImpl2(); // GetSimpleAudioVolume
            int GetSessionEnumerator(out IAudioSessionEnumerator sessionEnum);
        }

        [ComImport, Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IAudioSessionEnumerator
        {
            int GetCount(out int count);
            int GetSession(int index, out IAudioSessionControl session);
        }

        [ComImport, Guid("F4B1A599-7266-4319-A8CA-E70ACB11E8CD"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IAudioSessionControl { }

        [ComImport, Guid("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IAudioSessionControl2
        {
            int NotImpl1();  // GetState
            int NotImpl2();  // GetDisplayName
            int NotImpl3();  // SetDisplayName
            int NotImpl4();  // GetIconPath
            int NotImpl5();  // SetIconPath
            int NotImpl6();  // GetGroupingParam
            int NotImpl7();  // SetGroupingParam
            int NotImpl8();  // RegisterAudioSessionNotification
            int NotImpl9();  // UnregisterAudioSessionNotification
            int NotImpl10(); // GetSessionIdentifier
            int NotImpl11(); // GetSessionInstanceIdentifier
            int GetProcessId(out uint pid);
        }

        [ComImport, Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface ISimpleAudioVolume
        {
            int SetMasterVolume(float level, Guid eventContext);
            int GetMasterVolume(out float level);
            int SetMute(bool mute, Guid eventContext);
            int GetMute(out bool mute);
        }
    }
}