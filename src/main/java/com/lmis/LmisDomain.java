package com.lmis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

public class LmisDomain {

    public LmisDomain() {
        mDomains = new ArrayList();
    }

    public void add(String condition) {
        mDomains.add(condition);
    }

    public void add(String key, String ope, Object value) {
        JSONArray domain = new JSONArray();
        domain.put(key);
        domain.put(ope);
        if (value instanceof List)
            domain.put(listToArray(value));
        else
            domain.put(value);
        mDomains.add(domain);
    }

    public JSONArray listToArray(Object ids) {
        JSONArray jIds = new JSONArray();
        List object = (List) ids;
        try {
            Object obj;
            for (Iterator iterator = object.iterator(); iterator.hasNext(); jIds.put(obj))
                obj = iterator.next();

        } catch (Exception exception) {
        }
        return jIds;
    }

    public JSONArray getArray() {
        JSONArray result = new JSONArray();
        Object obj;
        for (Iterator iterator = mDomains.iterator(); iterator.hasNext(); result.put(obj))
            obj = iterator.next();

        return result;
    }

    public JSONObject get() {
        JSONArray result = new JSONArray();
        Object obj;
        for (Iterator iterator = mDomains.iterator(); iterator.hasNext(); result.put(obj))
            obj = iterator.next();

        JSONObject domain = new JSONObject();
        try {
            domain.put("domain", result);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return domain;
    }

    List mDomains;
}

