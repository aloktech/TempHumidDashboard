/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.imos.th.view;

import com.alibaba.fastjson.JSON;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.primefaces.model.chart.Axis;
import org.primefaces.model.chart.AxisType;
import org.primefaces.model.chart.CategoryAxis;
import org.primefaces.model.chart.LineChartModel;
import org.primefaces.model.chart.LineChartSeries;

/**
 *
 * @author Pintu
 */
@Log4j2
@Data
@SessionScoped
@Named("chartView")
public class TempHumidChartView implements Serializable {

    private ExecutorService service = Executors.newCachedThreadPool();
    private Client client;
    private LineChartModel chartModel;
    private LineChartSeries temperature;
    private LineChartSeries humidity;
    private Date startTime;
    private Date endTime;
    private long start;
    private long end;
    private double maxTemp = 0, minTemp = 0, maxHumid = 0, minHumid = 0, avgTemp = 0, avgHumid = 0;

    @PostConstruct
    public void config() {
        chartModel = new LineChartModel();
        chartModel.setTitle("Temperature and Humidity Chart");
        chartModel.setLegendPosition("ne");
        chartModel.setShowPointLabels(false);
        chartModel.getAxes().put(AxisType.X, new CategoryAxis("Time"));

        Axis yAxis = chartModel.getAxis(AxisType.Y);
        yAxis.setLabel("Percentage/\n\u00b0 Celcius");
        yAxis.setMin(10);
        yAxis.setMax(75);

        Axis xAxis = chartModel.getAxis(AxisType.X);
        xAxis.setTickAngle(45);

        temperature = new LineChartSeries();
        temperature.setLabel("Temperature");
        temperature.set(0, 0);

        humidity = new LineChartSeries();
        humidity.setLabel("Humidity");
        humidity.set(0, 0);

        chartModel.addSeries(temperature);
        chartModel.addSeries(humidity);

        startTime = new Date();
        endTime = new Date();
        setQueryTime();

        client = ClientBuilder.newClient();
    }

    private void setQueryTime() {
        Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTime(startTime);
        calendar.set(Calendar.HOUR, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        start = calendar.getTimeInMillis();

        calendar.setTime(endTime);
        calendar.set(Calendar.HOUR, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        end = calendar.getTimeInMillis();
    }

    public void extractDataFromSensor() {
        setQueryTime();
        log.info("Time is set");
        CompletableFuture.supplyAsync(() -> {
            log.info("Request send to the Sensor");
            WebTarget target = client.target("http://192.168.1.3:8090/tempHumid");
            target = target.queryParam("start", start);
            target = target.queryParam("end", end);
            Response response = target.request().get();
            String data;
            if (response.getStatus() == 202) {
                data = response.readEntity(String.class);
            } else {
                data = "[]";
            }
            System.out.println(data);
            log.info("Data received from the Sensor");
            return data;
        }).thenApplyAsync(d -> {
            log.info("Data converted into List");
            List<SensorData> list = JSON.parseArray(d, SensorData.class);
            SimpleDateFormat dFormat = new SimpleDateFormat("dd/MMM/yyyy");
            chartModel.setTitle(String.format("Temperature and Humidity Chart for %s", dFormat.format(new Date(list.get(0).getDay()))));
            System.out.println(list);
            return list;
        }).thenApplyAsync(d -> {
            try {
                avgHumid = avgTemp = 0;
                for (SensorData sd : d) {
                    if (maxTemp < sd.getTemperature()) {
                        maxTemp = sd.getTemperature();
                    }
                    if (minTemp == 0 || minTemp > sd.getTemperature()) {
                        minTemp = sd.getTemperature();
                    }
                    if (maxHumid < sd.getHumidity()) {
                        maxHumid = sd.getHumidity();
                    }
                    if (minHumid == 0 || minHumid > sd.getHumidity()) {
                        minHumid = sd.getHumidity();
                    }
                    avgHumid += sd.getHumidity();
                    avgTemp += sd.getTemperature();
                }
                BigDecimal size = new BigDecimal(d.size());
                avgHumid = new BigDecimal(avgHumid).divide(size, RoundingMode.HALF_EVEN).doubleValue();
                avgTemp = new BigDecimal(avgTemp).divide(size, RoundingMode.HALF_EVEN).doubleValue();
            } catch (Exception e) {
                log.error(e.getMessage());
            }

            return d;
        }).thenApplyAsync(d -> {
            chartModel.clear();
            log.info("Chart is cleared");
            temperature = new LineChartSeries();
            temperature.setLabel("Temperature");

            humidity = new LineChartSeries();
            humidity.setLabel("Humidity");
            log.info("Size of data: {}", d.size());
            d.stream().map(d1 -> {
                temperature.set(d1.getTimeStr(), d1.getTemperature());
                return d1;
            }).forEachOrdered(d1 -> {
                humidity.set(d1.getTimeStr(), d1.getHumidity());
            });
            chartModel.addSeries(temperature);
            chartModel.addSeries(humidity);

            log.info("Chart is populated");

            return chartModel;
        }).thenAcceptAsync(d -> {
            try {
                reload();
            } catch (IOException ex) {
                log.error("Reload failed: {}", ex.getMessage());
            }
        });
    }

    public void reload() throws IOException {
        ExternalContext ec = FacesContext.getCurrentInstance().getExternalContext();
        ec.redirect(((HttpServletRequest) ec.getRequest()).getRequestURI());
    }
}
