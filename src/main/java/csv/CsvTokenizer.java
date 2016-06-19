package csv;

import java.io.InputStream;
import java.io.IOException;

/**
 * A zero copy CSV stream parser with limited focus.
 *
 * This provides a simple API to parse a standard one-row-per-line stream.
 * Some support is provided for quoting, however the file is assumed to already
 * have been cleaned.
 *
 * To use the parser, create with the constructor. Then call parseLine() to parse the first line.
 * Read the fields using offset and length or getAsX methods. Once the line has been consumed, call
 * parseLine() again. Access the line buffer data using getLineBuffer(). By using getLineBuffer()
 * and the getOffset/getLength calls, consumers can process the parsed text fields in a (mostly) zero 
 * copy garbage free manner, and any unused fields be skipped.
 *
 * If reading from an InputStream, objects will be allocated to create a buffer.
 *
 * All fields for a given line are guaranteed to be in a single buffer. Different lines are not
 * guaranteed to be on the same buffer.  This parser is not thread safe. Quoted linebreaks 
 * are not supported. Support is for ISO-8859-1. Other encodings may work however they have not been
 * tested.
 *
 * Note - the bufferSize is important as a full chunk of memory this large will be allocated during read.
 *
 * TODO currently assumes a max line length of 8k for buffer flipping
 */
public class CsvTokenizer {

    /**
     * Creates a Csv Tokenizer to parse the stream.
     *
     * @param input         the stream to parse
     * @param bufferSize    the size of the buffer to create
     * @param maxColumns    the number of columns to anticipate in the input
     */
    public CsvTokenizer(InputStream input, int bufferSize, int maxColumns) {
        this.input = new InputStreamReader(input, bufferSize, 8192);
        NEWLINE = '\n';
        SEPARATOR = ',';
        QUOTE = '"';
        fieldStarts = new int[maxColumns];
        fieldEnds = new int[maxColumns];
    }

    /**
     * Creates a Stream Tokenizer to parse the stream.
     *
     * @param input         the stream to parse
     * @param bufferSize    the size of the buffer to create
     * @param maxColumns    the number of columns to anticipate in the input
     * @param newline       the new line character
     * @param separator     the field separator
     * @param quote         the field quote character
     */
    public CsvTokenizer(InputStream input, int bufferSize, int maxColumns, 
            byte newline, byte separator, byte quote) {
        this.input = new InputStreamReader(input, bufferSize, 8192);
        NEWLINE = newline;
        SEPARATOR = separator;
        QUOTE = quote;
        fieldStarts = new int[maxColumns];
        fieldEnds = new int[maxColumns];
    }

    /**
     * Creates a CsvTokenizer to parse a byte array.
     *
     * @param input         the array to parse
     * @param maxColumns    the number of columns to anticipate in the input
     */
    public CsvTokenizer(byte[] input, int maxColumns) {
        this.input = new ByteArrayReader(input);
        NEWLINE = '\n';
        SEPARATOR = ',';
        QUOTE = '"';
        fieldStarts = new int[maxColumns];
        fieldEnds = new int[maxColumns];
    }

    // TODO - these are currently ignored!!
    private final byte NEWLINE;
    private final byte SEPARATOR;
    private final byte QUOTE;

    /* input stream and buffer for storing collected bytes */
    private final StreamReader input;

    /* line and field start and end markers are tracked during parsing */
    private int lineStart = -1;
    private int lineEnd = -1;
    private final int[] fieldStarts;
    private final int[] fieldEnds;

    /* the parser requires some knowledge of where we are in the stream */
    private int currentColumn = 0;
    private boolean prevFieldWasQuoted = false;
    private boolean insideQuotes = false;

    /**
     * Advances a line to the next line.
     *
     * @return true if more fields, false if end of file
     * @throws IOException if an exception is found reading from the underlying stream
     */
    public boolean parseLine() throws IOException {
        /*
         * Parses a full line from the input stream and sets the internal frame up for fetching 
         * fields.  Basically this reads ALL fields in for the line before returning.
         */ 

        // give the stream reader an opportunity to flip buffer
        input.flipIfNeeded();

        // start line parse by setting to a known state
        currentColumn = 0;
        lineStart = (input.getParsePosition() + 1);
        fieldStarts[currentColumn] = lineStart;
        insideQuotes = false;
        prevFieldWasQuoted = false;

        return doParseLoop();
    }

    private class Config {
        private static final byte NEWLINE = '\n';
        private static final byte COMMA = ',';
        private static final byte QUOTE = '"';
    }

    private boolean doParseLoop() throws IOException {
        // read bytes and parse until we hit eof (-1) or newline
        while (true) {
            byte b = input.readNextByte();
            // shortcut skip processing 
            if (b != -1 && b != Config.NEWLINE && b != Config.COMMA && b != Config.QUOTE) continue;
            if (b == Config.COMMA) {
                processSeparator();
            } else if (b == -1) {
                processCharNewLine();
                return false;
            } else if (b == Config.NEWLINE) {
                processCharNewLine();
                return true;
            } else if (b == Config.QUOTE) {
                processCharQuote();
            }
        }
    }
  
    /**
     * Process a quoting character (for start or end of a field).
     */
    private void processCharQuote() throws IOException {
        // if we are at the start of a field, then start quoting
        if (input.getParsePosition() == fieldStarts[currentColumn]) {
            insideQuotes = true;
            prevFieldWasQuoted = true;
            fieldStarts[currentColumn]++;
        } else if (insideQuotes) {
            // don't update next column start position, the separator should do that
            insideQuotes = false;
        }
    }

    /**
     * Process a field separator.
     * 
     * Ignore the separator if it is inside a quoted region.
     */
    private void processSeparator() {
        if (insideQuotes) return;
        // if the prev field was a quote, we need to remove the extra quote from the end
        // otherwise we just mark it as a normal end of field (at one before current position)
        if (prevFieldWasQuoted) {
            fieldEnds[currentColumn] = input.getParsePosition() - 2;
            prevFieldWasQuoted = false;
        } else {
            fieldEnds[currentColumn] = input.getParsePosition() - 1;
        }
        currentColumn++;
        fieldStarts[currentColumn] = input.getParsePosition() + 1;

    }

    /**
     * Process end of line character.
     */
    private void processCharNewLine() {
        // if the prev field was a quote, we need to remove the extra quote from the end
        // otherwise we just mark it as a normal end of field (at one before current position)
        lineEnd = input.getParsePosition() - 1;
        if (prevFieldWasQuoted) {
            fieldEnds[currentColumn] = lineEnd - 1;
        } else {
            fieldEnds[currentColumn] = lineEnd;
        }
    }

    /* *****************
     * CORE EXTERNAL ACCESSORS
     * *****************/
    /**
     * Get the backing buffer for this line
     *
     * @return the backing buffer for the line
     */
    public byte[] getLineBuffer() {
        return input.getBuffer(); 
    }

    /**
     * Get the offset for the start of line in the backing buffer
     *
     * @return the line offset
     */ 
    public int getLineOffset() {
        return lineStart;
    }

    /**
     * Get the offset for the line length in the backing buffer
     *
     * @return the line length
     */ 
    public int getLineLength() {
        return lineEnd - lineStart + 1;
    }

    /**
     * Get the count of columns parsed in this line
     *
     * @return the count of parsed columns
     */
    public int getCount() {
        return currentColumn + 1;
    }

    /**
     * Gets the offset of a specific field on the line
     *
     * @return the field offset of the provided field
     */
    public int getOffset(int field) {
        return fieldStarts[field];
    }

    /**
     * Gets the length of a specific field on the line (in bytes)
     *
     * @return the field length of the provided field
     */
    public int getLength(int field) {
        return fieldEnds[field] - fieldStarts[field] + 1;
    }

    /**
     * Returns the given field as a String.
     *
     * Note - creates a new object
     *
     * @param field the field number
     * @return the field value as a String, in platform default encoding
     */
    public String getAsString(int field) {
        return new String(input.getBuffer(), getOffset(field), getLength(field));
    }
    
    /**
     * Returns the given field processed as an int.
     *
     * Overflow is not considered. No data type checking is performed.
     *
     * @param field the field number
     * @return the field falue as an int 
     */
    public int getAsInt(int field) {
        int sum = 0;
        for(int i = fieldStarts[field]; i <= fieldEnds[field]; i++) {
            sum *= 10;
            sum += (input.getBuffer()[i] - '0');
        }
        return sum;
    }

    /**
     * Returns the given field processed as a long.
     *
     * Overflow is not considered. No data type checking is performed.
     *
     * @param field the field number
     * @return the field value as a long
     */
    public long getAsLong(int field) {
        long sum = 0;
        for(int i = fieldStarts[field]; i <= fieldEnds[field]; i++) {
            sum *= 10;
            sum += (input.getBuffer()[i] - '0');
        }
        return sum;
    }

    /**
     * Returns the given field processed as a double.
     *
     * Overflow is not considered. No data type checking is performed.
     *
     * @param field the field number
     * @return the field value as a double
     */
    public double getAsDouble(int field) {
        long sum = 0;
        boolean hasSeenDecimal = false;
        long divisor = 1;
        byte[] buffer = input.getBuffer();
        for(int i = fieldStarts[field]; i <= fieldEnds[field]; i++) {
            byte b = buffer[i];
            if (b == '.') {
                hasSeenDecimal = true;
                divisor = 1;
            } else {
                sum = (sum * 10) + (b - '0');
                divisor *= 10;
            }
        }
        if (!hasSeenDecimal) divisor = 1;
        double result = ((double) sum) / divisor;
        return result;
    }

    public interface StreamReader {
        public int getParsePosition();
        public byte[] getBuffer();
        public byte readNextByte() throws IOException;
        public boolean flipIfNeeded();
    }

    /**
     * Reads from a straight byte array, zero copy.
     *
     * Do not modify the underlying array while parsing is in progress
     * unless you like surprises.
     */
    public class ByteArrayReader implements StreamReader {
        public ByteArrayReader(byte[] input) {
            this.buffer = input;
        }

        private byte[] buffer;
        private int parsePosition = -1;

        public int getParsePosition() {
            return parsePosition;
        }

        public byte[] getBuffer() {
            return buffer;
        }

        public boolean flipIfNeeded() {
            return false;
        }

        public byte readNextByte() {
            parsePosition++;
            if (parsePosition == buffer.length) return -1;
            return buffer[parsePosition];
        }
    }

    /**
     * Reads from an InputStream, creates interim buffers to host the data
     */
    public class InputStreamReader implements StreamReader {
        public InputStreamReader(InputStream input, int bufferSize, int maxLineLength) {
            this.input = input;
            buffer = new byte[bufferSize];
            flipSize = bufferSize - maxLineLength;
        }

        // input to read
        private final InputStream input;
        private byte[] buffer;
        
        // target size for flip operations
        private final int flipSize;

        // track how many bytes from the input stream have been read into the buffer
        // aka the high water mark
        private int maxread = 0;
        
        // and the current position we are reading from in the buffer
        private int parsePosition = -1;
       
        /**
         * The current parse position in the buffer
         *
         * @returns current parse position
         */
        public int getParsePosition() {
            return parsePosition;
        }

        /**
         * The current backing buffer
         *
         * @returns current buffer
         */
        public byte[] getBuffer() {
            return buffer;
        }

        /**
         * Flips the buffer if it is required. Stream Reader should only flip a
         * buffer if a client calls this.
         *
         * If a client fails to call this, it will likely result in running out of buffer
         * space. readNextByte() will return false if buffer fills.
         *
         * @returns true if the buffer was flipped, false if not flipped
         */
        public boolean flipIfNeeded() {
            if (parsePosition < flipSize) return false;

            // allocate a new buffer
            byte[] oldbuffer = buffer;
            buffer = new byte[buffer.length];

            // copy any remaining unparsed data over from the old buffer
            // data that we have not read is anything AFTER parseposition UP TO INCL maxread
            int newmaxread = 0;
            while(parsePosition < (maxread - 1)) {
                parsePosition++;
                buffer[newmaxread] = oldbuffer[parsePosition];
                newmaxread++;
            }

            // reset parseposition and maxread
            parsePosition = -1;
            maxread = newmaxread;
            return true;
        }

        /**
         * Returns next byte in the stream.
         *
         * Handles advancing the stream marker
         *
         * @return next byte in stream, or -1 if the stream is completed
         * @throws IOException if the underlying inputstream throws an IOException
         */
        public byte readNextByte() throws IOException {
            parsePosition++;
            if (ensureBufferAvailableToRead()) {
                return buffer[parsePosition];
            } else {
                return -1;
            }
        }

        /**
         * Ensures there are enough bytes available in the underlying buffer to
         * support reading the next parse position.
         *
         * @throws IOException if the stream could not be read
         * @return true if read was successful, false if end of stream or buffer full
         */
        private boolean ensureBufferAvailableToRead() throws IOException {
            while (parsePosition >= maxread) {
                if (maxread == buffer.length) return false;
                int read = input.read(buffer, maxread, buffer.length - maxread);
                if (read == -1) return false;
                maxread += read;
            }
            return true;
        }

    }
}

