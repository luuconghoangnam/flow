using System.Collections.Generic;
using System.Linq;
using System.Text.Json.Serialization;

namespace Flow.Core.Models;

[JsonPolymorphic(TypeDiscriminatorPropertyName = "type")]
[JsonDerivedType(typeof(RangedParts), "ranges")]
[JsonDerivedType(typeof(MediaSegments), "mediaSegments")]
public interface IParts
{
    IParts Clone();
}

public class RangedParts : IParts
{
    public List<RangedPart> List { get; set; } = new();

    public RangedParts()
    {
    }

    public RangedParts(List<RangedPart> list)
    {
        List = list;
    }

    public IParts Clone()
    {
        return new RangedParts(List.Select(p => new RangedPart(p.From, p.To, p.Current)).ToList());
    }
}

public class MediaSegments : IParts
{
    public List<MediaSegment> List { get; set; } = new();

    public MediaSegments()
    {
    }

    public MediaSegments(List<MediaSegment> list)
    {
        List = list;
    }

    public IParts Clone()
    {
        return new MediaSegments(List.Select(s => new MediaSegment(s.SegmentIndex, s.Link, s.Duration, s.IsCompleted, s.Length, s.Current)).ToList());
    }
}
