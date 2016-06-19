package benchmark;

import csv.CsvTokenizer;

public class CityZ {
    
    public int cityOffset;
    public int cityLength;
    public byte[] cityBuffer;
    public int population; 
    public double latitude;
    public double longitude;
   
    public CityZ(CsvTokenizer csv) {
        copyTo(csv, this);
    }

    public static void copyTo(CsvTokenizer csv, CityZ c) {
        c.cityBuffer = csv.getLineBuffer();
        c.cityOffset = csv.getOffset(2);
        c.cityLength = csv.getLength(2);
        c.population = csv.getAsInt(4);
        c.latitude = csv.getAsDouble(5);
        c.longitude = csv.getAsDouble(6);
    }
}
