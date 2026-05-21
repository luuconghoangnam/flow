using System;
using System.IO;

namespace Flow.Shared.Utils;

public static class Logger
{
    private static readonly string LogFilePath;
    private static readonly object Lock = new();

    static Logger()
    {
        LogFilePath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "FlowDownloadManager",
            "flow.log"
        );
    }

    public static void Info(string message)
    {
        WriteLog("INFO", message);
    }

    public static void Warning(string message)
    {
        WriteLog("WARN", message);
    }

    public static void Error(string message, Exception? ex = null)
    {
        if (ex != null)
        {
            WriteLog("ERROR", $"{message} - Exception: {ex.Message}{Environment.NewLine}{ex.StackTrace}");
        }
        else
        {
            WriteLog("ERROR", message);
        }
    }

    private static void WriteLog(string level, string message)
    {
        lock (Lock)
        {
            try
            {
                var dir = Path.GetDirectoryName(LogFilePath);
                if (dir != null && !Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }

                var timestamp = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.fff");
                var formatted = $"[{timestamp}] [{level}] {message}{Environment.NewLine}";
                File.AppendAllText(LogFilePath, formatted);
            }
            catch
            {
                // Silence logging errors
            }
        }
    }
}
