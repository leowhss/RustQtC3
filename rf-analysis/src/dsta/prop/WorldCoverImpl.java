package dsta.prop;

import java.io.File;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.DirectPosition2D;
import org.opengis.coverage.PointOutsideCoverageException;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class WorldCoverImpl implements WorldCover {
	private GeoTiffReader reader;
	private GridCoverage2D o1;
	private CoordinateReferenceSystem crs1;
	private double[] values = new double[1];
	private String filepath = "data"+File.separator+"worldcover"+File.separator+"ESA_WorldCover_10m_2021_V200_N00E102_Map.tif";
	
	public WorldCoverImpl() {
		try {
			File f = new File(filepath);
			System.out.println(f.getAbsolutePath());
			this.reader = new GeoTiffReader(f);
			this.o1 = ((GridCoverage2D)reader.read(null));
			this.crs1 = o1.getCoordinateReferenceSystem();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public int getCoverTypeCache(double lon, double lat) {
		try {
			DirectPosition dp1 = new DirectPosition2D(crs1, lon,lat);
			int cover_type = -1;
			cover_type = (int)o1.evaluate(dp1, values)[0];
			return cover_type;
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public static void main(String[] args) {
		WorldCoverImpl wc = new WorldCoverImpl();
		int covertype = wc.getCoverTypeCache(103.75, 1.33);
		System.out.println(covertype);
	}
}
