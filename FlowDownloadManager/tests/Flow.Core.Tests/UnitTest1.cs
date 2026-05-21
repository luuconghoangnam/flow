using Flow.Shared.Utils;
using Xunit;

namespace Flow.Core.Tests;

public class SharedUtilsTests
{
    [Theory]
    [InlineData(0, "0 B")]
    [InlineData(500, "500 B")]
    [InlineData(1024, "1 KB")]
    [InlineData(1048576, "1 MB")]
    public void TestFormatBytes_Binary(long bytes, string expected)
    {
        string actual = SizeFormatter.FormatBytes(bytes, useBinary: true, compact: false);
        Assert.Equal(expected, actual);
    }

    [Theory]
    [InlineData("valid_file.zip", true)]
    [InlineData("file/with/slash.zip", false)]
    [InlineData("file?with?question.zip", false)]
    [InlineData("", false)]
    [InlineData("   ", false)]
    public void TestIsValidFileName(string filename, bool expected)
    {
        bool actual = FileNameValidator.IsValidFileName(filename);
        Assert.Equal(expected, actual);
    }

    [Theory]
    [InlineData("C:\\Downloads", true)]
    [InlineData("", false)]
    [InlineData("   ", false)]
    public void TestIsValidPath(string path, bool expected)
    {
        bool actual = PathValidator.IsValidPath(path);
        Assert.Equal(expected, actual);
    }
}