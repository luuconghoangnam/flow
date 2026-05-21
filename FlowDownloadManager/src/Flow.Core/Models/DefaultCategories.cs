using System;
using System.Collections.Generic;

namespace Flow.Core.Models;

public static class DefaultCategories
{
    public static List<Category> GetDefaultCategories()
    {
        return new List<Category>
        {
            new Category
            {
                Id = 0,
                Name = "Compressed",
                Icon = "ZipFolder",
                AcceptedFileTypes = new List<string> { "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "dmg", "tgz" }
            },
            new Category
            {
                Id = 1,
                Name = "Programs",
                Icon = "ApplicationFolder",
                AcceptedFileTypes = new List<string> { "apk", "exe", "msi", "bat", "sh", "jar", "app", "deb", "rpm", "bin" }
            },
            new Category
            {
                Id = 2,
                Name = "Videos",
                Icon = "VideoFolder",
                AcceptedFileTypes = new List<string> { "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v", "3gp", "mpeg", "ts" }
            },
            new Category
            {
                Id = 3,
                Name = "Music",
                Icon = "MusicFolder",
                AcceptedFileTypes = new List<string> { "mp3", "wav", "aac", "flac", "ogg", "aiff", "wma", "m4a" }
            },
            new Category
            {
                Id = 4,
                Name = "Pictures",
                Icon = "PictureFolder",
                AcceptedFileTypes = new List<string> { "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "svg", "webp", "heic", "ico", "raw", "psd" }
            },
            new Category
            {
                Id = 5,
                Name = "Documents",
                Icon = "DocumentFolder",
                AcceptedFileTypes = new List<string> { "doc", "docx", "pdf", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "epub", "pages" }
            }
        };
    }
}
