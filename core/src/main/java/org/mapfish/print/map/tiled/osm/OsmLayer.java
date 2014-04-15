/*
 * Copyright (C) 2014  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print.map.tiled.osm;

import com.google.common.collect.Multimap;
import jsr166y.ForkJoinPool;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.styling.Style;
import org.mapfish.print.URIUtils;
import org.mapfish.print.attribute.map.MapBounds;
import org.mapfish.print.map.Scale;
import org.mapfish.print.map.tiled.AbstractTiledLayer;
import org.mapfish.print.map.tiled.TileCacheInformation;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.Nonnull;

/**
 * Strategy object for rendering Osm based layers.
 *
 * @author Jesse on 4/11/2014.
 */
public final class OsmLayer extends AbstractTiledLayer {
    private final OsmLayerParam param;
    private final ClientHttpRequestFactory requestFactory;

    /**
     * Constructor.
     *
     * @param forkJoinPool   the thread pool for doing the rendering.
     * @param rasterStyle    the style to use when drawing the constructed grid coverage on the map.
     * @param param          the information needed to create OSM requests.
     * @param requestFactory A factory for making http request objects.
     */
    public OsmLayer(final ForkJoinPool forkJoinPool, final Style rasterStyle, final OsmLayerParam param,
                    final ClientHttpRequestFactory requestFactory) {
        super(forkJoinPool, rasterStyle);
        this.param = param;
        this.requestFactory = requestFactory;
    }

    @Override
    protected TileCacheInformation createTileInformation(final MapBounds bounds, final Rectangle paintArea, final double dpi,
                                                         final boolean isFirstLayer) {
        return new OsmTileCacheInformation(bounds, paintArea, dpi, isFirstLayer);
    }

    private final class OsmTileCacheInformation extends TileCacheInformation {
        private final Scale scale;
        private final int resolutionIndex;

        public OsmTileCacheInformation(final MapBounds bounds, final Rectangle paintArea, final double dpi,
                                       final boolean isFirstLayer) {
            super(bounds, paintArea, dpi, OsmLayer.this.param);

            final double targetResolution = bounds.getScaleDenominator(paintArea, dpi).toResolution(bounds.getProjection(), dpi);

            Double[] resolutions = OsmLayer.this.param.resolutions;
            int pos = resolutions.length - 1;
            double result = resolutions[pos];
            for (int i = resolutions.length - 1; i >= 0; --i) {
                double cur = resolutions[i];
                if (cur <= targetResolution * OsmLayer.this.param.resolutionTolerance) {
                    result = cur;
                    pos = i;
                }
            }

            this.scale = Scale.fromResolution(result, bounds.getProjection(), dpi);
            this.resolutionIndex = pos;
        }

        @Nonnull
        @Override
        public ClientHttpRequest getTileRequest(final URI commonUri, final ReferencedEnvelope tileBounds,
                                                final Dimension tileSizeOnScreen, final int column, final int row)
                throws IOException, URISyntaxException {

            StringBuilder path = new StringBuilder();
            if (!commonUri.getPath().endsWith("/")) {
                path.append('/');
            }
            path.append(String.format("%02d", this.resolutionIndex));
            path.append('/').append(column);
            path.append('/').append(row);
            path.append('.').append(OsmLayer.this.param.imageFormat);


            final URI uri = URIUtils.setPath(commonUri, path.toString());
            return OsmLayer.this.requestFactory.createRequest(uri, HttpMethod.GET);
        }

        @Override
        protected void customizeQueryParams(final Multimap<String, String> result) {
            //not much query params for this protocol...
        }

        @Override
        public Scale getScale() {
            return this.scale;
        }

        @Override
        public double getLayerDpi() {
            return OsmLayer.this.param.dpi;
        }

        @Override
        public Dimension getTileSize() {
            return OsmLayer.this.param.getTileSize();
        }

        @Nonnull
        @Override
        protected ReferencedEnvelope getTileCacheBounds() {
            return new ReferencedEnvelope(OsmLayer.this.param.getMaxExtent(), this.bounds.getProjection());
        }
    }
}