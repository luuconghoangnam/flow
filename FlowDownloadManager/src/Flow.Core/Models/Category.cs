using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.RegularExpressions;

namespace Flow.Core.Models;

public class Category
{
    public long Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Icon { get; set; } = string.Empty;
    public string Path { get; set; } = string.Empty;
    public bool UsePath { get; set; } = true;
    public List<string> AcceptedFileTypes { get; set; } = new();
    public List<string> AcceptedUrlPatterns { get; set; } = new();
    public List<long> Items { get; set; } = new();

    public bool HasFileTypes => AcceptedFileTypes.Any();
    public bool HasUrlPattern => AcceptedUrlPatterns.Any();
    public bool HasFilters => HasFileTypes || HasUrlPattern;

    public bool AcceptFileName(string fileName)
    {
        if (!HasFileTypes)
        {
            return true;
        }

        return AcceptedFileTypes.Any(ext =>
            fileName.EndsWith($".{ext}", StringComparison.OrdinalIgnoreCase));
    }

    public string? GetDownloadPath()
    {
        return UsePath ? Path : null;
    }

    public bool AcceptUrl(string url)
    {
        if (!HasUrlPattern)
        {
            return true;
        }

        return AcceptedUrlPatterns.Any(pattern => WildcardMatch(pattern, url));
    }

    public Category WithExtraItems(IEnumerable<long> newItems)
    {
        return new Category
        {
            Id = Id,
            Name = Name,
            Icon = Icon,
            Path = Path,
            UsePath = UsePath,
            AcceptedFileTypes = new List<string>(AcceptedFileTypes),
            AcceptedUrlPatterns = new List<string>(AcceptedUrlPatterns),
            Items = Items.Concat(newItems).Distinct().ToList()
        };
    }

    private static bool WildcardMatch(string pattern, string input)
    {
        if (string.IsNullOrEmpty(pattern)) return false;
        
        try
        {
            // Escape special regex chars except * and ?
            string regexPattern = "^" + Regex.Escape(pattern)
                .Replace("\\*", ".*")
                .Replace("\\?", ".") + "$";
            
            return Regex.IsMatch(input, regexPattern, RegexOptions.IgnoreCase);
        }
        catch
        {
            return false;
        }
    }
}
