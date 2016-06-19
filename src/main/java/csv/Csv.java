package csv;

public class Csv {

    public static void main(String[] args) throws Exception {
        
        CsvTokenizer st = new CsvTokenizer(System.in, 512*1024, 15);

        long sum = 0;
        while (st.parseLine()) {
            sum += st.getCount();
        }

        System.out.println(sum);
    }
}
