package dsta.rfmap;

import java.net.URL;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.layers.mercator.BasicMercatorTiledImageLayer;
import gov.nasa.worldwind.layers.mercator.MercatorTextureTile;
import gov.nasa.worldwind.retrieve.HTTPRetriever;
import gov.nasa.worldwind.retrieve.Retriever;
import gov.nasa.worldwind.util.Level;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.Tile;
import gov.nasa.worldwind.util.TileUrlBuilder;

/**
 * Esri World Imagery (free, ArcGIS Online REST tile service, no API key required) satellite
 * base layer. Replaces WorldWind's built-in {@code MSVirtualEarthLayer}, whose free-tier Bing
 * imagery metadata API was retired by Microsoft (June 2025) and so no longer returns tiles.
 *
 * <p>Esri's tile path is {@code /tile/{z}/{y}/{x}} with Y measured top-down (standard slippy-map
 * convention), the same convention as OpenStreetMap's {@code {z}/{x}/{y}} tiles - WorldWind's own
 * {@code PlainMapTileLayer} (used for OSM) flips its internal bottom-up tile row into that
 * top-down Y for the URL; this layer does the same flip, just with Esri's Y-before-X path order.
 *
 * <p>{@link #downloadTexture} is overridden because the base class's implementation only
 * recognizes the exact string {@code "http"} as a valid retrieval protocol and refuses to even
 * attempt {@code https} URLs (logging "Unrecognized retrieval protocol" and returning) - Esri's
 * service requires HTTPS (plain HTTP just 301-redirects, which this old retriever doesn't
 * follow). The override is otherwise a straight copy of the base class logic, just accepting
 * both schemes before handing off to the same {@link HTTPRetriever}, which itself works fine
 * with HTTPS URLs since it is backed by {@code java.net.URLConnection}.
 */
public class EsriWorldImageryLayer extends BasicMercatorTiledImageLayer {

    private static final String SERVICE_URL =
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/";

    public EsriWorldImageryLayer() {
        super(createLevelSet());
        setName("Esri World Imagery");
    }

    private static gov.nasa.worldwind.util.LevelSet createLevelSet() {
        AVList params = new AVListImpl();
        params.setValue(AVKey.TILE_WIDTH, 256);
        params.setValue(AVKey.TILE_HEIGHT, 256);
        params.setValue(AVKey.DATA_CACHE_NAME, "Earth/EsriWorldImagery");
        params.setValue(AVKey.SERVICE, SERVICE_URL);
        params.setValue(AVKey.DATASET_NAME, "tile");
        params.setValue(AVKey.FORMAT_SUFFIX, ".jpg");
        params.setValue(AVKey.NUM_LEVELS, 19);
        params.setValue(AVKey.NUM_EMPTY_LEVELS, 0);
        params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA, gov.nasa.worldwind.geom.LatLon.fromDegrees(36, 36));
        params.setValue(AVKey.SECTOR, new gov.nasa.worldwind.layers.mercator.MercatorSector(
                -1.0, 1.0, gov.nasa.worldwind.geom.Angle.NEG180, gov.nasa.worldwind.geom.Angle.POS180));
        params.setValue(AVKey.TILE_URL_BUILDER, (TileUrlBuilder) (Tile tile, String imageFormat) -> {
            Level level = tile.getLevel();
            int zoom = tile.getLevelNumber() + 3;
            int x = tile.getColumn();
            int y = (1 << zoom) - 1 - tile.getRow();
            return new URL(level.getService() + zoom + "/" + y + "/" + x);
        });
        return new gov.nasa.worldwind.util.LevelSet(params);
    }

    @Override
    protected void downloadTexture(MercatorTextureTile tile) {
        if (!WorldWind.getRetrievalService().isAvailable()) {
            return;
        }
        URL url;
        try {
            url = tile.getResourceURL();
        } catch (java.net.MalformedURLException e) {
            Logging.logger().log(java.util.logging.Level.SEVERE,
                    Logging.getMessage("layers.TextureLayer.ExceptionCreatingTextureUrl", tile), e);
            return;
        }
        if (url == null) {
            return;
        }
        if (WorldWind.getNetworkStatus().isHostUnavailable(url)) {
            return;
        }

        String protocol = url.getProtocol();
        if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
            Logging.logger().severe(Logging.getMessage("layers.TextureLayer.UnknownRetrievalProtocol", url));
            return;
        }

        Retriever retriever = new HTTPRetriever(url, new BasicMercatorTiledImageLayer.DownloadPostProcessor(tile, this));
        retriever.setValue("URLRetriever.ExtractZipEntry", "true");

        Integer connectTimeout = AVListImpl.getIntegerValue(this, AVKey.URL_CONNECT_TIMEOUT);
        if (connectTimeout != null && connectTimeout > 0) {
            retriever.setConnectTimeout(connectTimeout);
        }
        Integer readTimeout = AVListImpl.getIntegerValue(this, AVKey.URL_READ_TIMEOUT);
        if (readTimeout != null && readTimeout > 0) {
            retriever.setReadTimeout(readTimeout);
        }
        Integer staleRequestLimit = AVListImpl.getIntegerValue(this, AVKey.RETRIEVAL_QUEUE_STALE_REQUEST_LIMIT);
        if (staleRequestLimit != null && staleRequestLimit > 0) {
            retriever.setStaleRequestLimit(staleRequestLimit);
        }

        WorldWind.getRetrievalService().runRetriever(retriever, tile.getPriority());
    }
}
