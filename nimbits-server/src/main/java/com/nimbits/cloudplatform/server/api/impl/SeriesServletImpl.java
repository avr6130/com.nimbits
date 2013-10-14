/*
 * Copyright (c) 2013 Nimbits Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.  See the License for the specific language governing permissions and limitations under the License.
 */

package com.nimbits.cloudplatform.server.api.impl;

import com.nimbits.cloudplatform.client.common.Utils;
import com.nimbits.cloudplatform.client.enums.EntityType;
import com.nimbits.cloudplatform.client.enums.ExportType;
import com.nimbits.cloudplatform.client.enums.Parameters;
import com.nimbits.cloudplatform.client.model.common.impl.CommonFactory;
import com.nimbits.cloudplatform.client.model.entity.Entity;
import com.nimbits.cloudplatform.client.model.entity.EntityName;
import com.nimbits.cloudplatform.client.model.point.Point;
import com.nimbits.cloudplatform.client.model.value.Value;
import com.nimbits.cloudplatform.server.api.ApiServlet;
import com.nimbits.cloudplatform.server.gson.GsonFactory;
import com.nimbits.cloudplatform.server.transactions.entity.EntityServiceFactory;
import com.nimbits.cloudplatform.server.transactions.entity.service.EntityService;
import com.nimbits.cloudplatform.server.transactions.value.ValueServiceFactory;
import org.apache.commons.lang3.Range;
import org.springframework.stereotype.Service;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;


@Service("series")
public class SeriesServletImpl extends ApiServlet  implements org.springframework.web.HttpRequestHandler {


    private static final long serialVersionUID = 1L;
    public static final int LIMIT = 1000;
    private final EntityService service = EntityServiceFactory.getInstance();

    @Override
    public void doGet(final HttpServletRequest req, final HttpServletResponse resp) {

        try {
            doInit(req, resp, ExportType.plain);


            String segStr = req.getParameter(Parameters.seg.getText());
            final String startDate = req.getParameter(Parameters.sd.getText());
            final String endDate = req.getParameter(Parameters.ed.getText());


            Range timespan = null;

            if (!Utils.isEmptyString(startDate) && !Utils.isEmptyString(endDate)) {
                try {
                    timespan = Range.between(startDate, endDate);
                } catch (Exception e) {
                    timespan = null;
                }
            }


            int count = Utils.isEmptyString(getParam(Parameters.count)) ? 10 : Integer.valueOf(getParam(Parameters.count));

            if (count > LIMIT) {
                count = LIMIT;
            }
            if (Utils.isEmptyString(segStr)) {
                segStr = "0";
            }


            String name = getParam(Parameters.point);
            if (Utils.isEmptyString(name)) {
                name = getParam(Parameters.name);
            }


            final PrintWriter out = resp.getWriter();

            if (Utils.isEmptyString(name)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } else {


                final EntityName pointName = CommonFactory.createName(name, EntityType.point);
                List<Entity> points = service.getEntityByName(user, pointName, EntityType.point);
                if (! points.isEmpty()) {
                     Point point = (Point) points.get(0);

                    // final Point point = (Point) EntityServiceImpl.getEntityByKey(e.getKey(), PointEntity.class.getName());
                    if (point == null) {

                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    } else {
                        final List<Value> values;
                        if (!Utils.isEmptyString(segStr) && timespan != null) {

                            int seg = Integer.valueOf(segStr);

                            values = ValueServiceFactory.getInstance().getDataSegment(point, timespan, seg, seg + 1000);

                        } else {

                            values = ValueServiceFactory.getInstance().getTopDataSeries(point, count);

                        }


                        String result = GsonFactory.getInstance().toJson(values);
                        out.println(result);
                        resp.setStatus(HttpServletResponse.SC_OK);
                        out.close();
                    }
                }
                else {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
            }
        } catch (IOException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.addHeader("ERROR", e.getMessage());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.addHeader("ERROR", e.getMessage());
        }
    }




    @Override
    public void handleRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (isPost(req)) {

            doPost(req, resp);
        }
        else {
            doGet(req, resp);
        }

    }
}
