using System;
using System.IO;
using System.Text.Json;

namespace Flow.Core.Storage;

public class TransactionalFileSaver
{
    private readonly JsonSerializerOptions _options;

    public TransactionalFileSaver()
    {
        _options = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            WriteIndented = false
        };
    }

    public string GetBakFile(string filePath) => $"{filePath}.tmp";

    public void WriteObject<T>(string filePath, T obj)
    {
        string text = JsonSerializer.Serialize(obj, _options);
        WriteText(filePath, text);
    }

    public void WriteText(string filePath, string text)
    {
        string bakFile = GetBakFile(filePath);
        string? directory = Path.GetDirectoryName(filePath);
        
        if (!string.IsNullOrEmpty(directory) && !Directory.Exists(directory))
        {
            Directory.CreateDirectory(directory);
        }

        try
        {
            // Write to the temporary file
            File.WriteAllText(bakFile, text);

            // Perform atomic move/swap to target file
            if (File.Exists(filePath))
            {
                File.Move(bakFile, filePath, overwrite: true);
            }
            else
            {
                File.Move(bakFile, filePath);
            }
        }
        catch (Exception ex)
        {
            // Clean up temp file on failure
            try
            {
                if (File.Exists(bakFile))
                {
                    File.Delete(bakFile);
                }
            }
            catch { }

            throw new IOException($"Failed to transactionally write file {filePath}.", ex);
        }
    }

    public string? ReadText(string filePath)
    {
        try
        {
            if (!File.Exists(filePath)) return null;
            return File.ReadAllText(filePath);
        }
        catch
        {
            return null;
        }
    }

    public T? ReadObject<T>(string filePath)
    {
        try
        {
            string? text = ReadText(filePath);
            if (string.IsNullOrEmpty(text)) return default;
            return JsonSerializer.Deserialize<T>(text, _options);
        }
        catch
        {
            return default;
        }
    }
}
