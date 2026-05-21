using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using Flow.Core.Exceptions;

namespace Flow.Core.Connection;

public record ContentRangeValue(
    (long Start, long End)? Range,
    long? FullSize
);

public class HttpResponseInfo : IResponseInfo
{
    private static readonly Regex AsciiFileNameRegex =
        new(@"filename=([""']?)(?<fileName>.*?[^\\])\1(?:;\s*|$)", RegexOptions.IgnoreCase | RegexOptions.Compiled);

    private static readonly Regex Utf8FileNameRegex =
        new(@"filename\*=UTF-8''(?<fileName>[^;\s]+)(?:;\s*|$)", RegexOptions.IgnoreCase | RegexOptions.Compiled);

    private static readonly Regex MimeRegex =
        new(@"=\?(?<charset>[^?]+)\?(?<encoding>[BQ])\?(?<encodedText>[^?]+)\?=", RegexOptions.IgnoreCase | RegexOptions.Compiled);

    public int StatusCode { get; }
    public string Message { get; }
    public string RequestUrl { get; }
    public Dictionary<string, string> RequestHeaders { get; }
    public Dictionary<string, string> ResponseHeaders { get; }

    public HttpResponseInfo(
        int statusCode,
        string message,
        string requestUrl,
        Dictionary<string, string>? requestHeaders = null,
        Dictionary<string, string>? responseHeaders = null)
    {
        StatusCode = statusCode;
        Message = message;
        RequestUrl = requestUrl;
        RequestHeaders = requestHeaders ?? new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        ResponseHeaders = responseHeaders ?? new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

        // Lazy evaluation fields
        IsSuccessFul = StatusCode >= 200 && StatusCode <= 299;
        RequiresAuth = StatusCode == 401;

        if (ResponseHeaders.TryGetValue("content-length", out var clStr) && long.TryParse(clStr, out long cl) && cl >= 0L)
        {
            ContentLength = cl;
        }

        if (ResponseHeaders.TryGetValue("content-range", out var crStr))
        {
            ContentRange = ParseContentRange(crStr);
        }

        IsPartial = StatusCode == 206;

        if (ContentLength.HasValue)
        {
            TotalLength = IsPartial && ContentRange?.FullSize != null ? ContentRange.FullSize.Value : ContentLength.Value;
        }

        RequireBasicAuth = RequiresAuth && ResponseHeaders.TryGetValue("www-authenticate", out var authHeader) &&
                           authHeader.Contains("basic", StringComparison.OrdinalIgnoreCase);

        ResumeSupport = IsPartial && ContentLength.HasValue && ContentRange?.FullSize != null;

        IsWebPage = ResponseHeaders.TryGetValue("content-type", out var ctHeader) &&
                    ctHeader.Contains("text/html", StringComparison.OrdinalIgnoreCase);

        FileName = ExtractFileName();
    }

    public bool IsSuccessFul { get; }
    public long? ContentLength { get; }
    public ContentRangeValue? ContentRange { get; }
    public long? TotalLength { get; }
    public bool RequiresAuth { get; }
    public bool RequireBasicAuth { get; }
    public bool IsPartial { get; }
    public bool ResumeSupport { get; }
    public bool IsWebPage { get; }
    public string? FileName { get; }

    public string? LastModified => ResponseHeaders.TryGetValue("last-modified", out var val) ? val : null;
    public string? Etag => ResponseHeaders.TryGetValue("etag", out var val) ? val : null;

    public HttpResponseInfo ExpectSuccess()
    {
        if (!IsSuccessFul)
        {
            throw new UnSuccessfulResponseException(StatusCode, Message);
        }
        return this;
    }

    private string? ExtractFileName()
    {
        string? nameFromHeader = null;
        if (ResponseHeaders.TryGetValue("content-disposition", out var dispValue))
        {
            nameFromHeader = ExtractFileNameFromContentDisposition(dispValue);
        }

        string fileName = nameFromHeader ?? ExtractNameFromLink(RequestUrl) ?? string.Empty;

        if (IsWebPage && !string.IsNullOrEmpty(fileName))
        {
            fileName = ReplaceExtension(fileName, "html", force: true);
        }

        return string.IsNullOrEmpty(fileName) ? null : fileName;
    }

    private static string? ExtractFileNameFromContentDisposition(string value)
    {
        var utf8Match = Utf8FileNameRegex.Match(value);
        if (utf8Match.Success)
        {
            try
            {
                return FilenameDecoder.Decode(utf8Match.Groups["fileName"].Value);
            }
            catch
            {
                // Fallback
            }
        }

        var asciiMatch = AsciiFileNameRegex.Match(value);
        if (asciiMatch.Success)
        {
            string fileName = asciiMatch.Groups["fileName"].Value;
            try
            {
                fileName = DecodeMimeEncodedFilename(fileName);
            }
            catch
            {
                // Fallback
            }

            try
            {
                return FilenameDecoder.Decode(fileName);
            }
            catch
            {
                return fileName;
            }
        }

        return null;
    }

    private static string DecodeMimeEncodedFilename(string input)
    {
        return MimeRegex.Replace(input, match =>
        {
            try
            {
                string charsetName = match.Groups["charset"].Value;
                string encoding = match.Groups["encoding"].Value.ToUpperInvariant();
                string encodedText = match.Groups["encodedText"].Value;

                byte[] bytes = encoding switch
                {
                    "B" => Convert.FromBase64String(encodedText),
                    "Q" => DecodeMimeQuotedPrintable(encodedText),
                    _ => throw new NotSupportedException()
                };

                var charsetEncoding = GetCharsetEncoding(charsetName);
                return charsetEncoding.GetString(bytes);
            }
            catch
            {
                return match.Value;
            }
        });
    }

    private static byte[] DecodeMimeQuotedPrintable(string encoded)
    {
        var list = new List<byte>();
        int i = 0;
        while (i < encoded.Length)
        {
            char c = encoded[i];
            if (c == '=' && i + 2 < encoded.Length)
            {
                string hex = encoded.Substring(i + 1, 2);
                if (byte.TryParse(hex, System.Globalization.NumberStyles.HexNumber, null, out byte b))
                {
                    list.Add(b);
                    i += 3;
                }
                else
                {
                    list.Add((byte)c);
                    i++;
                }
            }
            else if (c == '_')
            {
                list.Add((byte)' ');
                i++;
            }
            else
            {
                list.Add((byte)c);
                i++;
            }
        }
        return list.ToArray();
    }

    private static Encoding GetCharsetEncoding(string name)
    {
        try
        {
            return Encoding.GetEncoding(name);
        }
        catch
        {
            try
            {
                Encoding.RegisterProvider(CodePagesEncodingProvider.Instance);
                return Encoding.GetEncoding(name);
            }
            catch
            {
                return Encoding.UTF8;
            }
        }
    }

    private static string? ExtractNameFromLink(string link)
    {
        try
        {
            if (!Uri.TryCreate(link, UriKind.Absolute, out var uri))
            {
                return null;
            }

            var segments = uri.Segments;
            string? foundSegment = null;
            for (int i = segments.Length - 1; i >= 0; i--)
            {
                string s = segments[i].Trim('/', ' ', '\\');
                if (!string.IsNullOrWhiteSpace(s))
                {
                    foundSegment = s;
                    break;
                }
            }

            if (foundSegment != null)
            {
                try
                {
                    return FilenameDecoder.Decode(foundSegment);
                }
                catch
                {
                    return foundSegment;
                }
            }

            return uri.Host.Replace('.', '_');
        }
        catch
        {
            return null;
        }
    }

    private static string ReplaceExtension(string fileName, string newExtension, bool force = false)
    {
        if (string.IsNullOrEmpty(fileName)) return fileName;
        if (!force && Path.HasExtension(fileName)) return fileName;
        return Path.ChangeExtension(fileName, newExtension);
    }

    private static ContentRangeValue? ParseContentRange(string value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;
        try
        {
            string actualValue = value.StartsWith("bytes ", StringComparison.OrdinalIgnoreCase)
                ? value.Substring(6).Trim()
                : value.Trim();

            if (string.IsNullOrWhiteSpace(actualValue)) return null;

            var parts = actualValue.Split('/');
            if (parts.Length < 2) return null;

            string rangeString = parts[0].Trim();
            string sizeString = parts[1].Trim();

            (long Start, long End)? range = null;
            if (rangeString != "*")
            {
                var rangeParts = rangeString.Split('-');
                if (rangeParts.Length >= 2)
                {
                    range = (long.Parse(rangeParts[0]), long.Parse(rangeParts[1]));
                }
            }

            long? fullSize = null;
            if (sizeString != "*")
            {
                if (long.TryParse(sizeString, out long size))
                {
                    fullSize = size;
                }
            }

            return new ContentRangeValue(range, fullSize);
        }
        catch
        {
            return null;
        }
    }
}

public static class FilenameDecoder
{
    public static string Decode(string encoded, Encoding? encoding = null)
    {
        if (string.IsNullOrEmpty(encoded)) return encoded;
        encoding ??= Encoding.UTF8;

        var stringBuilder = new StringBuilder();
        int strIndex = 0;
        byte[]? bytes = null;

        while (strIndex < encoded.Length)
        {
            char ch = encoded[strIndex];
            if (ch == '%')
            {
                int byteIndex = 0;
                bytes ??= new byte[(encoded.Length - strIndex) / 3];

                while (true)
                {
                    if (strIndex + 2 >= encoded.Length)
                    {
                        throw new ArgumentException($"Incomplete percent encoding at position {strIndex}");
                    }

                    string hex = encoded.Substring(strIndex + 1, 2);
                    bytes[byteIndex++] = Convert.ToByte(hex, 16);
                    strIndex += 3;

                    if (strIndex < encoded.Length)
                    {
                        ch = encoded[strIndex];
                        if (ch == '%')
                        {
                            continue;
                        }
                    }
                    break;
                }
                stringBuilder.Append(encoding.GetString(bytes, 0, byteIndex));
            }
            else
            {
                stringBuilder.Append(ch);
                strIndex++;
            }
        }

        return bytes != null ? stringBuilder.ToString() : encoded;
    }
}
