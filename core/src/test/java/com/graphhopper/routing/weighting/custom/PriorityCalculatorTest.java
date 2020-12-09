/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper.routing.weighting.custom;

import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.CustomModel;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.GHUtility;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PriorityCalculatorTest {

    private final EncodingManager em;
    private final EdgeIteratorState edge;

    PriorityCalculatorTest() {
        // in this test we use the same edge for every test and check the calculated priority for different custom models
        // and depending on additional properties of the edge we set in the tests
        FlagEncoder encoder = new CarFlagEncoder();
        em = EncodingManager.create(encoder);
        edge = GHUtility.setSpeed(60, true, true, encoder, new GraphBuilder(em).create().edge(0, 1).setDistance(1000));
    }

    @Test
    public void priority() {
        CustomModel model = new CustomModel();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("*", 0.3);
        model.getPriority().put(RoadClass.KEY, map);
        assertEquals(0.3, calcPriority(edge, model));
        // our edge might or might not be affected
        EnumEncodedValue<RoadClass> roadClass = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        edge.set(roadClass, RoadClass.PRIMARY);
        model.getPriority().clear();
        assertEquals(1.0, calcPriority(edge, new CustomModel()));
        map.clear();
        map.put(RoadClass.PRIMARY.toString(), 0.7);
        model.getPriority().put(RoadClass.KEY, map);
        assertEquals(0.7, calcPriority(edge, model));
    }

    @Test
    public void invalidPriority() {
        CustomModel model = new CustomModel();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("*", 1.1);
        model.getPriority().put(RoadClass.KEY, map);
        try {
            assertEquals(1.0, calcPriority(edge, model));
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("priority.road_class cannot be bigger than 1.0, was 1.1"), e.getMessage());
        }
        // negative values are not allowed
        try {
            map.put("*", -5);
            assertEquals(1.0, calcPriority(edge, model));
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("priority.road_class cannot be smaller than 0.0, was -5.0"), e.getMessage());
        }
    }

    @Test
    public void priorityFactorMultiple() {
        EnumEncodedValue<RoadClass> roadClass = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<RoadEnvironment> roadEnvironment = em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        edge.set(roadClass, RoadClass.PRIMARY);
        edge.set(roadEnvironment, RoadEnvironment.BRIDGE);
        Map<String, Object> roadClassMap = new LinkedHashMap<>();
        roadClassMap.put(RoadClass.PRIMARY.toString(), 0.7);
        Map<String, Object> roadEnvironmentMap = new LinkedHashMap<>();
        roadEnvironmentMap.put(RoadEnvironment.BRIDGE.toString(), 0.5);
        CustomModel model = new CustomModel();
        model.getPriority().put(RoadClass.KEY, roadClassMap);
        model.getPriority().put(RoadEnvironment.KEY, roadEnvironmentMap);
        assertEquals(0.35, calcPriority(edge, model));
    }

    @Test
    public void directionDependent() {
        EnumEncodedValue<RoadClass> roadClass = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        DecimalEncodedValue maxSpeedEnc = em.getDecimalEncodedValue(MaxSpeed.KEY);
        edge.set(roadClass, RoadClass.PRIMARY);
        edge.set(maxSpeedEnc, 110, 50);

        Map<String, Object> maxSpeedMap = new LinkedHashMap<>();
        maxSpeedMap.put("<100", 0.5);

        CustomModel model = new CustomModel();
        model.getPriority().put(MaxSpeed.KEY, maxSpeedMap);
        assertEquals(1.0, calcPriority(edge, model));

        PriorityCalculator priorityCalculator = new PriorityCalculator(model, em);
        assertEquals(0.5, priorityCalculator.calcPriority(edge, true));
    }

    private double calcPriority(EdgeIteratorState edge, CustomModel model) {
        PriorityCalculator priorityCalculator = new PriorityCalculator(model, em);
        return priorityCalculator.calcPriority(edge, false);
    }
}