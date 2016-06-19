# ZeroCSV

A low-overhead CSV parser, which allows if you like to produce nearly zero garbage. Integrates well with Disruptor. Very quick at most things.

Pretty heavily customised so not as general purpose as some other libraries.

## Performance results


CAVEAT - It does have quite a few limitations in terms of character support and similar so it is not an apples to apples comparison. But, the majority of text I work with is encoded in a simple character set with only single row per line so this is sufficient for my needs and significantly faster. Your mileage may vary. Pull requests encouraged!

Now for the performance results: First CSV-Game and then Quick-CSV-Streamer results.

If you are already using a Streams based API I would recommend using QuickCSV.

The below results don't really highlight the end-to-end processing benefits you get from using zero through your entire processing change but are nonetheless interesting to look at. Zero easily beats most of the general purpose parsers, however QuickCSV is actually well ahead in its test suite.

```
CSV-Game result       CSV      Count
------------------------------------
CSVeed                4.871    -
BeanIO                1.126    -
Commons               0.848    -
Java                  0.328    -
OpenCSV               0.390    0.429
Univocity             0.272    0.313
ZeroCsv               0.141    0.161
```

And using the Quick-CSV test set:

```
Quick-CSV Test Set    Parse time  
----------------------------------------------
OpenCSV               1870 +- 86 ms/op
ZeroCsv (I'Stream)     786 +- 29 ms/op
QuickCSV Sequential    622 +- 45 ms/op
ZeroCsv (byte[])       512 +- 32 ms/op
ZeroCsv (no String)    371 +- 33 ms/op
QuickCSV Parallel      232 +- 11 ms/op
```

A sample of the API:

```
// provide a filename, the temporary buffer size,
// and the number of columns we should anticipate
String filename = "hello.csv"
int BUFFER_SIZE = 512*1024;
int MAX_COLS = 15;

// create the tokenizer
CsvTokenizer csv = new CsvTokenizer(new FileInputStream(filename), BUFFER_SIZE, MAX_COLS);

// start parsing
while (csv.parseLine()) {
  // fetch column count
  System.out.println("column count = " + csv.getCount());
  // fetch a value as a string (creates garbage)
  System.out.println("3rd column value = " + csv.getAsString(2));
  // perform a write to System.out (without creating a String)
  System.out.write(csv.getBuffer(), csv.getOffset(2), csv.getLength(2));
  
}
```

## Working with Disruptor

Using this with Disruptor is great. Your domain objects should instead of being objects, simply become pointers into the buffer. If you perform your CSV parsing and stream into a set of ints and a pointer to a byte[] then the Disruptor entries become very compact and simple to work with.

```
// in main loop
while(csv.parseLine()) {
    ringBuffer.publishEvent((line, seq, t) ->
        CityZ.copyTo(t, line), csv));
}
// see the CityZ code for an example
```

Other helpers are available such as getAsLong, getAsInt, getAsDouble. This allows for a nice API to work with the existing Java APIs that take a byte array, offset, and length. If the API gets cumbersome it is simple to switch to using Strings etc. This also allows you to skip deserialisation of columns that are not required.

If you are using an InputStream to process huge files, the Zero parser will allocate smaller buffers and rotate through them for you. As long as you eventually complete processing through disruptor the buffers will be released. (This will in fact generate garbage).

## What's special

A significant part of the difference is in a few very small areas:
- Custom double and int parsing implementation
- Surprise branching shortcuts
- Avoid copying of buffers and instantiation of objects where possible
- Take some shortcuts with error checking

## Further investigation

I might start benchmarking and profiling to compare my implementation vs QuickCSV. I do notice the character processing stream is inverted. But I also think the QuickCSV implementation has a lot of additional complexity. Perhaps if the two ideas are merged ....

QuickCSV also uses a custom Double parsing implementation.

In addition, QuickCSV seems to perform much quicker than ZeroCSV on the InputStream'd inputs. I'd like to investigate why that is.

## The code?

Code is on github at https://github.com/jasonk000/zerocsv
