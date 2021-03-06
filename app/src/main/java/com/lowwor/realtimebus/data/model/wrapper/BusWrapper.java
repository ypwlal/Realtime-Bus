package com.lowwor.realtimebus.data.model.wrapper;

import com.google.gson.annotations.SerializedName;
import com.lowwor.realtimebus.data.model.Bus;

import java.util.List;

/**
 * Created by lowworker on 2015/10/14.
 */
public class BusWrapper {

    /**
     * d : [{"BusNumber":"粤C17824","CurrentStation":"洲仔"},{"BusNumber":"粤C17867","CurrentStation":"长园电力"},{"BusNumber":"粤C18274","CurrentStation":"宝莱特科技"},{"BusNumber":"粤C18311","CurrentStation":"湾仔沙"}]
     */

    @SerializedName("data")
    private List<Bus> data;

    private int flag;

    public List<Bus> getData() {
        return data;
    }

    public void setData(List<Bus> data) {
        this.data = data;
    }


}
