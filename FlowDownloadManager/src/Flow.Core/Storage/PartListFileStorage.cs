using System;
using System.IO;
using System.Threading.Tasks;
using Flow.Core.Models;

namespace Flow.Core.Storage;

public class PartListFileStorage : IDownloadPartListDb
{
    private readonly string _partsFolder;
    private readonly TransactionalFileSaver _fileSaver;

    public PartListFileStorage(string partsFolder, TransactionalFileSaver fileSaver)
    {
        _partsFolder = partsFolder;
        _fileSaver = fileSaver;

        if (!Directory.Exists(_partsFolder))
        {
            Directory.CreateDirectory(_partsFolder);
        }
    }

    public string GetFileForId(long id)
    {
        return Path.Combine(_partsFolder, $"{id}.json");
    }

    public async Task<IParts?> GetPartsAsync(long id)
    {
        return await Task.Run(() =>
        {
            string filePath = GetFileForId(id);
            return _fileSaver.ReadObject<IParts>(filePath);
        });
    }

    public async Task SetPartsAsync(long id, IParts parts)
    {
        await Task.Run(() =>
        {
            try
            {
                string filePath = GetFileForId(id);
                _fileSaver.WriteObject(filePath, parts);
            }
            catch { }
        });
    }

    public async Task RemovePartsAsync(long id)
    {
        await Task.Run(() =>
        {
            try
            {
                string filePath = GetFileForId(id);
                if (File.Exists(filePath))
                {
                    File.Delete(filePath);
                }
            }
            catch { }
        });
    }

    public async Task ClearAsync()
    {
        await Task.Run(() =>
        {
            try
            {
                if (Directory.Exists(_partsFolder))
                {
                    var files = Directory.GetFiles(_partsFolder, "*.json");
                    foreach (string file in files)
                    {
                        File.Delete(file);
                    }
                }
            }
            catch { }
        });
    }
}
