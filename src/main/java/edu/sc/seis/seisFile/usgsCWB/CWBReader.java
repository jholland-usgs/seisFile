package edu.sc.seis.seisFile.usgsCWB;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import edu.sc.seis.seisFile.MSeedQueryReader;
import edu.sc.seis.seisFile.dataSelectWS.DataSelectException;
import edu.sc.seis.seisFile.mseed.DataRecord;
import edu.sc.seis.seisFile.mseed.SeedFormatException;
import edu.sc.seis.seisFile.mseed.SeedRecord;


public class CWBReader implements MSeedQueryReader {
    
    public CWBReader() {
        this(DEFAULT_HOST);
    }
    
    public CWBReader(String host) {
        this(host, DEFAULT_PORT);
    }
    
    public CWBReader(String host, int port) {
        this.host = host;
        this.port = port;
    }

    protected String createQuery(String network, String station, String location, String channel) throws IOException, DataSelectException, SeedFormatException {
        String query = leftPad(network.trim(), 2);
        query += station.trim();  // station not supposed to be left padded, assume anything not N L C is S
        query += leftPad(location.trim(), 2);
        query += leftPad(channel.trim(), 3);
        return query;
    }
    
    public String createQuery(String network, String station, String location, String channel, Date begin, float durationSeconds) throws IOException, DataSelectException, SeedFormatException {
        String query = "'-s' '"+createQuery(network, station, location, channel)+"' ";
        SimpleDateFormat longFormat = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss");
        longFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        query += "'-b' '" + longFormat.format(begin)+"' ";
        query += "'-d' '" + (int)Math.ceil(durationSeconds)+"' ";
        query += "'-t' 'ms'";
        query += "\t";
        return query;
    }
    
    public List<DataRecord> read(String query) throws IOException, DataSelectException, SeedFormatException {
        Socket socket = new Socket(host, port);
        socket.setReceiveBufferSize(512000);
        OutputStream outtcp = socket.getOutputStream();
        outtcp.write(query.getBytes());
        outtcp.flush();
        PushbackInputStream bif = new PushbackInputStream(new BufferedInputStream(socket.getInputStream()), 1);
        DataInputStream in = new DataInputStream(bif);
        List<DataRecord> records = new ArrayList<DataRecord>();
        while (true) {
            try {
                int nextByte = bif.read();
                if (nextByte == '<') {
                    // end of stream marked with "<EOR>" so end read
                    break;
                } else {
                    bif.unread(nextByte);
                }
                SeedRecord sr = SeedRecord.read(in);
                if (sr instanceof DataRecord) {
                    records.add((DataRecord)sr);
                } else {
                    System.err.println("None data record found, skipping...");
                }
            } catch (EOFException e) {
                // end of data?
                break;
            }
        }
        in.close();
        outtcp.close();
        socket.close();
        return records;
    }
    
    protected String leftPad(String in, int length) {
        if (in.length() == length) { 
            return in; 
        } else {
            return leftPad(in+"@", length);
        }
    }

    private String host;
    private int port;
    
    public static final String DEFAULT_HOST = "cwb-pub.cr.usgs.gov";
    
    public static final int DEFAULT_PORT = 2061;
}
