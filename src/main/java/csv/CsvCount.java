package csv;

import java.io.FileInputStream;

public class CsvCount {

    public static void main(String[] args) throws Exception {
        final String filename = args[1];
        final int field = Integer.parseInt(args[0]) - 1;
        
        CsvTokenizer st = new CsvTokenizer(new FileInputStream(filename), 512*1024, 15);

        long sum = 0;
        while (st.parseLine()) {
            sum += st.getAsInt(field);
        }

        System.out.println(sum);
    }
}
