namespace Flow.Integration;

public class ApiQueueModel
{
    public long Id { get; set; }
    public string Name { get; set; } = string.Empty;

    public ApiQueueModel() { }

    public ApiQueueModel(long id, string name)
    {
        Id = id;
        Name = name;
    }
}
