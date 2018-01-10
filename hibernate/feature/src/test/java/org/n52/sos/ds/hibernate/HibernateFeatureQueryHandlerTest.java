/*
 * Copyright (C) 2012-2018 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
package org.n52.sos.ds.hibernate;

import static org.n52.sos.util.JTSHelperForTesting.randomCoordinate;
import static org.n52.sos.util.ReverseOf.reverseOf;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.junit.Before;
import org.junit.Test;

import org.n52.faroe.ConfigurationError;
import org.n52.shetland.ogc.sos.Sos2Constants;
import org.n52.iceland.util.Constants;
import org.n52.shetland.ogc.gml.AbstractFeature;
import org.n52.shetland.ogc.om.features.SfConstants;
import org.n52.shetland.ogc.om.features.samplingFeatures.SamplingFeature;
import org.n52.shetland.ogc.ows.exception.NoApplicableCodeException;
import org.n52.shetland.ogc.ows.exception.OwsExceptionReport;
import org.n52.shetland.util.JTSHelper;
import org.n52.sos.ds.FeatureQueryHandlerQueryObject;
import org.n52.sos.ds.hibernate.entities.FeatureOfInterest;
import org.n52.sos.ds.hibernate.entities.FeatureOfInterestType;
import org.n52.sos.util.GeometryHandler;
import org.n52.sos.util.JTSHelperForTesting;
import org.n52.sos.util.builder.SamplingFeatureBuilder;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

/**
 * TODO JavaDoc
 *
 * @author <a href="mailto:c.autermann@52north.org">Christian Autermann</a>
 *
 * @since 4.0.0
 */
public class HibernateFeatureQueryHandlerTest extends HibernateTestCase {

    @Before
    public void setUp() throws ConfigurationError {
        GeometryHandler.getInstance().setStorageEpsg(4326);
        GeometryHandler.getInstance().setStorage3DEpsg(4979);
        GeometryHandler.getInstance().setEpsgCodesWithNorthingFirstAxisOrder("2044-2045;2081-2083;2085-2086;2093;2096-2098;2105-2132;2169-2170;2176-2180;2193;2200;2206-2212;2319;2320-2462;2523-2549;2551-2735;2738-2758;2935-2941;2953;3006-3030;3034-3035;3058-3059;3068;3114-3118;3126-3138;3300-3301;3328-3335;3346;3350-3352;3366;3416;4001-4999;20004-20032;20064-20092;21413-21423;21473-21483;21896-21899;22171;22181-22187;22191-22197;25884;27205-27232;27391-27398;27492;28402-28432;28462-28492;30161-30179;30800;31251-31259;31275-31279;31281-31290;31466-31700");
        GeometryHandler.getInstance().setDatasourceNorthingFirst(false);
    }

    @Test
    public void shouldCreateValidModelDomainFeature() throws OwsExceptionReport {
        Session session = getSession();
        try {
            final String id = "id";
            final String type = SfConstants.SAMPLING_FEAT_TYPE_SF_SAMPLING_POINT;
            FeatureOfInterest feature = create(1, id, null, "name", "url", createFeatureOfInterestType(1, type));
            String version = Sos2Constants.SERVICEVERSION;
            AbstractFeature result =
                    new HibernateFeatureQueryHandler().createSosAbstractFeature(feature,
                            new FeatureQueryHandlerQueryObject().setVersion(version).setConnection(session));
            final AbstractFeature expectedResult =
                    SamplingFeatureBuilder.aSamplingFeature().setFeatureType(type).setIdentifier(id).build();
            assertThat(expectedResult, is(result));
        } catch (HibernateException he) {
            throw new NoApplicableCodeException().causedBy(he);
        } finally {
            returnSession(session);
        }
    }

    public FeatureOfInterest create(long id, String identifier, Geometry geom, String name, String url,
            FeatureOfInterestType type) {
        FeatureOfInterest featureOfInterest = new FeatureOfInterest();
        featureOfInterest.setIdentifier(identifier);
        featureOfInterest.setFeatureOfInterestId(id);
        // featureOfInterest.setNames(name);
        featureOfInterest.setGeom(geom);
        featureOfInterest.setUrl(url);
        featureOfInterest.setFeatureOfInterestType(type);
        return featureOfInterest;
    }

    private FeatureOfInterestType createFeatureOfInterestType(int id, String type) {
        FeatureOfInterestType featureOfInterestType = new FeatureOfInterestType();
        featureOfInterestType.setFeatureOfInterestTypeId(id);
        featureOfInterestType.setFeatureOfInterestType(type);
        return featureOfInterestType;
    }

    @Test
    public void shouldSwitchCoordinatesForEpsg4326() throws OwsExceptionReport {
        GeometryFactory factory = JTSHelper.getGeometryFactoryForSRID(Constants.EPSG_WGS84);
        Geometry geometry = factory.createPoint(randomCoordinate());
        Geometry switched = GeometryHandler.getInstance().switchCoordinateAxisFromToDatasourceIfNeeded(geometry);

        assertThat(GeometryHandler.getInstance().isNorthingFirstEpsgCode(4326), is(true));
        assertThat(switched, is(notNullValue()));
        assertThat(switched, is(instanceOf(geometry.getClass())));
        assertThat(switched, is(not(sameInstance(geometry))));
        assertThat(switched, is(reverseOf(geometry)));
    }

    @Test
    public void shouldSwitchCoordinatesForSosAbstractFeature() throws OwsExceptionReport {
        Session session = getSession();
        try {
            GeometryFactory factory = JTSHelper.getGeometryFactoryForSRID(4326);
            Geometry geometry = factory.createPoint(JTSHelperForTesting.randomCoordinate());
            FeatureOfInterest feature =
                    create(1, "id", geometry, "name", "url", createFeatureOfInterestType(1, "type"));
            AbstractFeature sosFeature =
                    new HibernateFeatureQueryHandler().createSosAbstractFeature(feature,
                            new FeatureQueryHandlerQueryObject().setVersion(Sos2Constants.SERVICEVERSION)
                                    .setConnection(session));

            assertThat(GeometryHandler.getInstance().isNorthingFirstEpsgCode(4326), is(true));
            assertThat(sosFeature, is(notNullValue()));
            assertThat(sosFeature, is(instanceOf(SamplingFeature.class)));

            SamplingFeature ssf = (SamplingFeature) sosFeature;

            assertThat(ssf.getGeometry(), is(notNullValue()));
            assertThat(ssf.getGeometry(), is(instanceOf(geometry.getClass())));
            assertThat(ssf.getGeometry(), is(not(sameInstance(geometry))));
            assertThat(ssf.getGeometry(), is(reverseOf(geometry)));
        } catch (HibernateException he) {
            throw new NoApplicableCodeException().causedBy(he);
        } finally {
            returnSession(session);
        }
    }

    @Test
    public void shouldNotSwitchCoordinatesForEpsg2181() throws OwsExceptionReport {
        GeometryFactory factory = JTSHelper.getGeometryFactoryForSRID(2094);
        Geometry geometry = factory.createPoint(randomCoordinate());
        Geometry switched = GeometryHandler.getInstance().switchCoordinateAxisFromToDatasourceIfNeeded(geometry);

        assertThat(GeometryHandler.getInstance().isNorthingFirstEpsgCode(2094), is(false));
        assertThat(switched, is(notNullValue()));
        assertThat(switched, is(instanceOf(geometry.getClass())));
        // assertThat(switched, is(sameInstance(geometry)));
        // assertThat(switched, is(not(reverseOf(geometry))));
    }

    @Test
    public void shouldNotSwitchCoordinatesForSosAbstractFeature() throws OwsExceptionReport {
        Session session = getSession();
        try {
            GeometryFactory factory = JTSHelper.getGeometryFactoryForSRID(2181);
            Geometry geometry = factory.createPoint(JTSHelperForTesting.randomCoordinate());

            assertThat(GeometryHandler.getInstance().isNorthingFirstEpsgCode(2181), is(false));

            FeatureOfInterest feature =
                    create(1, "id", geometry, "name", "url", createFeatureOfInterestType(1, "type"));
            AbstractFeature sosFeature =
                    new HibernateFeatureQueryHandler().createSosAbstractFeature(feature,
                            new FeatureQueryHandlerQueryObject().setVersion(Sos2Constants.SERVICEVERSION)
                                    .setConnection(session));

            assertThat(GeometryHandler.getInstance().isNorthingFirstEpsgCode(4326), is(true));
            assertThat(sosFeature, is(notNullValue()));
            assertThat(sosFeature, is(instanceOf(SamplingFeature.class)));

            SamplingFeature ssf = (SamplingFeature) sosFeature;

            assertThat(ssf.getGeometry(), is(notNullValue()));
            assertThat(ssf.getGeometry(), is(instanceOf(geometry.getClass())));
            assertThat(ssf.getGeometry(), is(sameInstance(geometry)));
            assertThat(ssf.getGeometry(), is(not(reverseOf(geometry))));
        } catch (HibernateException he) {
            throw new NoApplicableCodeException().causedBy(he);
        } finally {
            returnSession(session);
        }
    }

    @Test
    public void shouldNotFailOnReferencedFeature() throws OwsExceptionReport {
        String url = "http://example.com/wfs?service=WFS&version=1.1.0&request=GetFeature&typeName=waterdata:sampling&featureid=foi1";
        SamplingFeature ssf = new SamplingFeature(null);
        ssf.setUrl(url);
        // TODO check
//        assertThat(insertFeature(ssf, null), is(url));
    }
}
