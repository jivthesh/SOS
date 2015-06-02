/**
 * Copyright (C) 2012-2015 52°North Initiative for Geospatial Open Source
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

import static org.n52.sos.ds.hibernate.CacheFeederSettingDefinitionProvider.CACHE_THREAD_COUNT;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.n52.iceland.cache.WritableContentCache;
import org.n52.iceland.config.annotation.Configurable;
import org.n52.iceland.config.annotation.Setting;
import org.n52.iceland.ds.CacheFeederHandler;
import org.n52.iceland.ds.ConnectionProvider;
import org.n52.iceland.ds.FeatureQueryHandler;
import org.n52.iceland.ds.HibernateDatasourceConstants;
import org.n52.iceland.exception.ConfigurationException;
import org.n52.iceland.i18n.I18NDAORepository;
import org.n52.iceland.i18n.I18NSettings;
import org.n52.iceland.i18n.LocaleHelper;
import org.n52.iceland.ogc.ows.CompositeOwsException;
import org.n52.iceland.ogc.ows.OwsExceptionReport;
import org.n52.iceland.ogc.ows.ServiceIdentificationFactory;
import org.n52.iceland.util.CollectionHelper;
import org.n52.iceland.util.Validation;
import org.n52.sos.ds.hibernate.cache.InitialCacheUpdate;
import org.n52.sos.ds.hibernate.cache.base.OfferingCacheUpdate;

/**
 * Implementation of the interface CacheFeederDAO
 *
 * @since 4.0.0
 */
@Configurable
public class SosCacheFeederDAO implements CacheFeederHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SosCacheFeederDAO.class);

    /**
     * Defines the number of threads available in the thread pool of the cache
     * update executor service.
     */
    private int cacheThreadCount = 5;
    private Locale defaultLocale;
    private I18NDAORepository i18NDAORepository;
    private FeatureQueryHandler featureQueryHandler;
    private ServiceIdentificationFactory serviceIdentificationFactory;
    private HibernateSessionHolder sessionHolder;

    @Inject
    public void setConnectionProvider(ConnectionProvider connectionProvider) {
        this.sessionHolder = new HibernateSessionHolder(connectionProvider);
    }

    @Setting(I18NSettings.I18N_DEFAULT_LANGUAGE)
    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = LocaleHelper.fromString(defaultLocale);
    }

    @Inject
    public void setServiceIdentificationFactory(ServiceIdentificationFactory serviceIdentificationFactory) {
        this.serviceIdentificationFactory = serviceIdentificationFactory;
    }

    @Inject
    public void setFeatureQueryHandler(FeatureQueryHandler featureQueryHandler) {
        this.featureQueryHandler = featureQueryHandler;
    }

    @Inject
    public void setI18NDAORepository(I18NDAORepository i18NDAORepository) {
        this.i18NDAORepository = i18NDAORepository;
    }

    @Setting(CACHE_THREAD_COUNT)
    public void setCacheThreadCount(int threads) throws ConfigurationException {
        Validation.greaterZero("Cache Thread Count", threads);
        this.cacheThreadCount = threads;
    }

    @Override
    public void updateCache(WritableContentCache cache) throws OwsExceptionReport {
        checkCacheNotNull(cache);
        List<OwsExceptionReport> errors = CollectionHelper.synchronizedList();
        Session session = null;
        try {
            InitialCacheUpdate update = new InitialCacheUpdate(
                    this.cacheThreadCount,
                    this.defaultLocale,
                    this.i18NDAORepository,
                    this.featureQueryHandler,
                    this.sessionHolder.getConnectionProvider(),
                    this.serviceIdentificationFactory);
            session = this.sessionHolder.getSession();
            update.setCache(cache);
            update.setErrors(errors);
            update.setSession(session);

            LOGGER.info("Starting cache update");
            long cacheUpdateStartTime = System.currentTimeMillis();

            update.execute();

            logCacheLoadTime(cacheUpdateStartTime);
        } catch (HibernateException he) {
            LOGGER.error("Error while updating ContentCache!", he);
        } finally {
            this.sessionHolder.returnSession(session);
        }
        if (!errors.isEmpty()) {
            throw new CompositeOwsException(errors);
        }
    }

    @Override
    public void updateCacheOfferings(WritableContentCache cache, Collection<String> offeringsNeedingUpdate)
            throws OwsExceptionReport {
        checkCacheNotNull(cache);
        if (CollectionHelper.isEmpty(offeringsNeedingUpdate)) {
            return;
        }
        List<OwsExceptionReport> errors = CollectionHelper.synchronizedList();
        Session session = this.sessionHolder.getSession();
        OfferingCacheUpdate update = new OfferingCacheUpdate(
                this.cacheThreadCount,
                this.defaultLocale,
                this.i18NDAORepository,
                this.featureQueryHandler,
                this.sessionHolder.getConnectionProvider());
        update.setCache(cache);
        update.setErrors(errors);
        update.setSession(session);

        LOGGER.info("Starting offering cache update for {} offering(s)", offeringsNeedingUpdate.size());
        long cacheUpdateStartTime = System.currentTimeMillis();

        try {
            update.execute();
        } catch (HibernateException he) {
            LOGGER.error("Error while updating ContentCache!", he);
        } finally {
            this.sessionHolder.returnSession(session);
        }

        logCacheLoadTime(cacheUpdateStartTime);

        if (!errors.isEmpty()) {
            throw new CompositeOwsException(errors);
        }
    }

    private void checkCacheNotNull(WritableContentCache cache) {
        if (cache == null) {
            throw new NullPointerException("cache is null");
        }
    }

    private void logCacheLoadTime(long startTime) {
        Period cacheLoadPeriod = new Period(startTime, System.currentTimeMillis());
        LOGGER.info("Cache load finished in {} ({} seconds)",
                PeriodFormat.getDefault().print(cacheLoadPeriod.normalizedStandard()),
                cacheLoadPeriod.toStandardSeconds());
    }

}
