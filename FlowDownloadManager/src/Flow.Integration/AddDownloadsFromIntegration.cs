using System;
using System.Collections.Generic;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Flow.Integration;

public class AddDownloadOptionsFromIntegration
{
    [JsonPropertyName("silentAdd")]
    public bool SilentAdd { get; set; } = false;

    [JsonPropertyName("silentStart")]
    public bool SilentStart { get; set; } = false;
}

public class AddDownloadsFromIntegration
{
    [JsonPropertyName("items")]
    public List<IDownloadCredentialsFromIntegration> Items { get; set; } = new();

    [JsonPropertyName("options")]
    public AddDownloadOptionsFromIntegration Options { get; set; } = new();

    public static AddDownloadsFromIntegration CreateFromRequest(string jsonData)
    {
        var options = new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        };

        try
        {
            var result = JsonSerializer.Deserialize<AddDownloadsFromIntegration>(jsonData, options);
            if (result != null && result.Items != null && result.Items.Count > 0)
            {
                return result;
            }
        }
        catch (JsonException)
        {
            // Fallback for backwards compatibility: when it is just a list of items
            try
            {
                var items = JsonSerializer.Deserialize<List<HttpDownloadCredentialsFromIntegration>>(jsonData, options);
                if (items != null)
                {
                    return new AddDownloadsFromIntegration
                    {
                        Items = new List<IDownloadCredentialsFromIntegration>(items),
                        Options = new AddDownloadOptionsFromIntegration
                        {
                            SilentAdd = false,
                            SilentStart = false
                        }
                    };
                }
            }
            catch (Exception ex)
            {
                throw new InvalidOperationException("Failed to decode downloads from integration request.", ex);
            }
        }

        throw new InvalidOperationException("Decoded integration request was null or had no items.");
    }
}
