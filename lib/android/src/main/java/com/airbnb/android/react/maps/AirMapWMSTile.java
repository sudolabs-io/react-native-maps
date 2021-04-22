package com.airbnb.android.react.maps;

import android.content.Context;
import android.util.Log;

import com.facebook.react.bridge.ReadableMap;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class AirMapWMSTile extends AirMapFeature {
  private static final double[] mapBound = {-20037508.34789244, 20037508.34789244};
  private static final double FULL = 20037508.34789244 * 2;
  private static final String HTTPS_KEYWORD = "https";
  private static final String ORIGIN = AirMapUrlTile.class.getSimpleName();

  class AIRMapGSUrlTileProvider extends UrlTileProvider {
    private String urlTemplate;
    private int width;
    private int height;
    public AIRMapGSUrlTileProvider(int width, int height, String urlTemplate) {
      super(width, height);
      this.urlTemplate = urlTemplate;
      this.width = width;
      this.height = height;
    }

    @Override
    public synchronized URL getTileUrl(int x, int y, int zoom) {
      if(AirMapWMSTile.this.maximumZ > 0 && zoom > maximumZ) {
          return null;
      }
      if(AirMapWMSTile.this.minimumZ > 0 && zoom < minimumZ) {
          return null;
      }
      double[] bb = getBoundingBox(x, y, zoom);
      String s = this.urlTemplate
          .replace("{minX}", Double.toString(bb[0]))
          .replace("{minY}", Double.toString(bb[1]))
          .replace("{maxX}", Double.toString(bb[2]))
          .replace("{maxY}", Double.toString(bb[3]))
          .replace("{width}", Integer.toString(width))
          .replace("{height}", Integer.toString(height));
      URL url = null;
      try {
        url = new URL(s);
      } catch (MalformedURLException e) {
        throw new AssertionError(e);
      }
      return url;
    }

    class AIRMapUrlTile implements TileProvider {
      private static final int BUFFER_SIZE = 4 * 1024;
      private int width;
      private int height;
      private ReadableMap requestProperties;

      AIRMapUrlTile(int width, int height, ReadableMap requestProperties) {
        this.width = width;
        this.height = height;
        this.requestProperties = requestProperties;
      }

      private byte[] readTileImage(int x, int y, int zoom) {
        InputStream inputStream = null;
        ByteArrayOutputStream buffer = null;
        URL url = getTileUrl(x, y, zoom);
        if (url == null)
          return null;

        try {
          if (getUrlTemplate().contains(HTTPS_KEYWORD)) {
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            for (Map.Entry<String, Object> entry : requestProperties.toHashMap().entrySet()) {
              connection.addRequestProperty(entry.getKey(), (String) entry.getValue());
            }
            connection.connect();
            inputStream = connection.getInputStream();
          } else {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            for (Map.Entry<String, Object> entry : requestProperties.toHashMap().entrySet()) {
              connection.addRequestProperty(entry.getKey(), (String) entry.getValue());
            }
            connection.connect();
            inputStream = connection.getInputStream();
          }
          buffer = new ByteArrayOutputStream();

          byte[] data = new byte[BUFFER_SIZE];
          int nRead;
          while ((nRead = inputStream.read(data, 0, BUFFER_SIZE)) != -1) {
            buffer.write(data, 0, nRead);
          }
          buffer.flush();
          return buffer.toByteArray();
        } catch (IOException | OutOfMemoryError e) {
          Log.e(ORIGIN, e.getMessage());
          return null;
        } finally {
          if (inputStream != null) try {
            inputStream.close();
          } catch (Exception e) {
            Log.e(ORIGIN, e.getMessage());
          }
          if (buffer != null) try {
            buffer.close();
          } catch (Exception e) {
            Log.e(ORIGIN, e.getMessage());
          }
        }
      }

      @Override
      public Tile getTile(int x, int y, int zoom) {
        byte[] image = readTileImage(x, y, zoom);
        return image == null ? TileProvider.NO_TILE : new Tile(this.width, this.height, image);
      }
    }

    private double[] getBoundingBox(int x, int y, int zoom) {
      double tile = FULL / Math.pow(2, zoom);
      return new double[]{
              mapBound[0] + x * tile,
              mapBound[1] - (y + 1) * tile,
              mapBound[0] + (x + 1) * tile,
              mapBound[1] - y * tile
      };
    }

    public String getUrlTemplate() {
      return this.urlTemplate;
    }

    public void setUrlTemplate(String urlTemplate) {
      this.urlTemplate = urlTemplate;
    }
  }

  private TileOverlayOptions tileOverlayOptions;
  private TileOverlay tileOverlay;
  private AIRMapGSUrlTileProvider tileProvider;
  private AIRMapGSUrlTileProvider.AIRMapUrlTile customTileProvider;

  private String urlTemplate;
  private float zIndex;
  private float maximumZ;
  private float minimumZ;
  private int tileSize;
  private float opacity;
  private ReadableMap requestProperties;

  public AirMapWMSTile(Context context) {
    super(context);
  }

  public void setUrlTemplate(String urlTemplate) {
    this.urlTemplate = urlTemplate;
    if (tileProvider != null) {
      tileProvider.setUrlTemplate(urlTemplate);
    }
    if (tileOverlay != null) {
      tileOverlay.clearTileCache();
    }
  }

  public void setZIndex(float zIndex) {
    this.zIndex = zIndex;
    if (tileOverlay != null) {
      tileOverlay.setZIndex(zIndex);
    }
  }

  public void setMaximumZ(float maximumZ) {
    this.maximumZ = maximumZ;
    if (tileOverlay != null) {
      tileOverlay.clearTileCache();
    }
  }

  public void setMinimumZ(float minimumZ) {
    this.minimumZ = minimumZ;
    if (tileOverlay != null) {
      tileOverlay.clearTileCache();
    }
  }
  public void setTileSize(int tileSize) {
    this.tileSize = tileSize;
    if (tileOverlay != null) {
      tileOverlay.clearTileCache();
    }
  }
  public void setOpacity(float opacity) {
    this.opacity = opacity;
    if (tileOverlay != null) {
        tileOverlay.setTransparency(1-opacity);
    }
  }

  void setRequestProperties(ReadableMap requestProperties) {
    this.requestProperties = requestProperties;
    if (tileOverlay != null) {
      tileOverlay.clearTileCache();
    }
  }

  public TileOverlayOptions getTileOverlayOptions() {
    if (tileOverlayOptions == null) {
      tileOverlayOptions = createTileOverlayOptions();
    }
    return tileOverlayOptions;
  }

  private TileOverlayOptions createTileOverlayOptions() {
    TileOverlayOptions options = new TileOverlayOptions();
    options.zIndex(zIndex);
    options.transparency(1-opacity);
    this.tileProvider = new AIRMapGSUrlTileProvider(this.tileSize, this.tileSize, this.urlTemplate);
    if(this.requestProperties != null) {
      this.customTileProvider = this.tileProvider.new AIRMapUrlTile(this.tileSize, this.tileSize, this.requestProperties);
      options.tileProvider(this.customTileProvider);
    } else {
      options.tileProvider(this.tileProvider);
    }
    return options;
  }

  @Override
  public Object getFeature() {
    return tileOverlay;
  }

  @Override
  public void addToMap(GoogleMap map) {
    this.tileOverlay = map.addTileOverlay(getTileOverlayOptions());
  }

  @Override
  public void removeFromMap(GoogleMap map) {
    tileOverlay.remove();
  }
}
