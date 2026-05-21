using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Flow.Core.Models;

namespace Flow.Core.Storage;

public class DownloadListFileStorage : IDownloadListDb
{
    private readonly string _downloadListFolder;
    private readonly TransactionalFileSaver _fileSaver;
    
    // Per-ID asynchronous semaphores to protect concurrent reads/writes on the same file
    private readonly ConcurrentDictionary<long, SemaphoreSlim> _fileLocks = new();
    
    // Global lock to synchronize ID allocation and additions
    private readonly SemaphoreSlim _addLock = new(1, 1);

    public DownloadListFileStorage(string downloadListFolder, TransactionalFileSaver fileSaver)
    {
        _downloadListFolder = downloadListFolder;
        _fileSaver = fileSaver;

        if (!Directory.Exists(_downloadListFolder))
        {
            Directory.CreateDirectory(_downloadListFolder);
        }
    }

    private string GetDownloadItemFile(long id)
    {
        return Path.Combine(_downloadListFolder, $"{id}.json");
    }

    private string LastIdFile => Path.Combine(_downloadListFolder, "last_id.txt");

    private async Task<T?> WithLockAsync<T>(long id, Func<Task<T?>> action) where T : class
    {
        var semaphore = _fileLocks.GetOrAdd(id, _ => new SemaphoreSlim(1, 1));
        await semaphore.WaitAsync();
        try
        {
            return await action();
        }
        finally
        {
            semaphore.Release();
        }
    }

    private async Task WithLockAsync(long id, Func<Task> action)
    {
        var semaphore = _fileLocks.GetOrAdd(id, _ => new SemaphoreSlim(1, 1));
        await semaphore.WaitAsync();
        try
        {
            await action();
        }
        finally
        {
            semaphore.Release();
        }
    }

    public async Task<List<IDownloadItem>> GetAllAsync()
    {
        return await Task.Run(async () =>
        {
            if (!Directory.Exists(_downloadListFolder)) return new List<IDownloadItem>();

            var files = Directory.GetFiles(_downloadListFolder, "*.json");
            var items = new List<IDownloadItem>();

            foreach (string file in files)
            {
                string name = Path.GetFileNameWithoutExtension(file);
                if (long.TryParse(name, out long id))
                {
                    var item = await GetByIdAsync(id);
                    if (item != null)
                    {
                        items.Add(item);
                    }
                }
            }

            return items;
        });
    }

    public async Task<IDownloadItem?> GetByIdAsync(long id)
    {
        return await WithLockAsync(id, () =>
        {
            string filePath = GetDownloadItemFile(id);
            var item = _fileSaver.ReadObject<IDownloadItem>(filePath);
            return Task.FromResult(item);
        });
    }

    public async Task AddAsync(IDownloadItem item)
    {
        await _addLock.WaitAsync();
        try
        {
            await WithLockAsync(item.Id, async () =>
            {
                string filePath = GetDownloadItemFile(item.Id);
                _fileSaver.WriteObject(filePath, item);

                long lastId = await GetLastIdAsync();
                if (lastId < item.Id)
                {
                    SetLastId(item.Id);
                }
            });
        }
        finally
        {
            _addLock.Release();
        }
    }

    public async Task UpdateAsync(IDownloadItem item)
    {
        await WithLockAsync(item.Id, () =>
        {
            string filePath = GetDownloadItemFile(item.Id);
            _fileSaver.WriteObject(filePath, item);
            return Task.CompletedTask;
        });
    }

    public async Task RemoveByIdAsync(long itemId)
    {
        await WithLockAsync(itemId, () =>
        {
            string filePath = GetDownloadItemFile(itemId);
            if (File.Exists(filePath))
            {
                File.Delete(filePath);
            }
            return Task.CompletedTask;
        });
    }

    public async Task RemoveAsync(IDownloadItem item)
    {
        await RemoveByIdAsync(item.Id);
    }

    private void SetLastId(long id)
    {
        try
        {
            _fileSaver.WriteObject(LastIdFile, id);
        }
        catch { }
    }

    public async Task<long> GetLastIdAsync()
    {
        return await Task.Run(() =>
        {
            long? lastId = null;
            try
            {
                // Try reading from last_id.txt
                lastId = _fileSaver.ReadObject<long>(LastIdFile);
            }
            catch { }

            if (lastId == null || lastId.Value == 0)
            {
                lastId = GetLastIdFromFiles();
                SetLastId(lastId.Value);
            }

            return lastId.Value;
        });
    }

    private long GetLastIdFromFiles()
    {
        if (!Directory.Exists(_downloadListFolder)) return -1L;

        var files = Directory.GetFiles(_downloadListFolder, "*.json");
        long maxId = -1L;

        foreach (string file in files)
        {
            string name = Path.GetFileNameWithoutExtension(file);
            if (long.TryParse(name, out long id))
            {
                if (id > maxId) maxId = id;
            }
        }

        return maxId;
    }
}
