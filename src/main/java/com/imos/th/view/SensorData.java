package com.imos.th.view;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.Data;

/**
 *
 * @author Pintu
 */
@Data
public class SensorData implements Serializable {

    private static final long serialVersionUID = 5832101484242298342L;

    private static final String ipAddress = getDefaultLocalHost();
    
    private double temperature;
    private double humidity;
    private String timeStr;
    private final long time;
    private final long day;
    

    public SensorData() {
        time = System.currentTimeMillis();
        day = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String getDefaultLocalHost() {
        String localhost;
        try {
            localhost = Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException ex) {
            Logger.getLogger(SensorData.class.getName()).log(Level.SEVERE, null, ex);
            localhost = "";
        }
        return localhost;
    }
}
