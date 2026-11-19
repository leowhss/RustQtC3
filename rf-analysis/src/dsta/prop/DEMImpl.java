package dsta.prop;

import java.io.File;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.DirectPosition2D;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class DEMImpl implements DEM {
	private GeoTiffReader reader;
	private GridCoverage2D o1;
	private CoordinateReferenceSystem crs1;
	private double[] values = new double[1];
	private String filepath = "data"+File.separator+"dted"+File.separator+"srtm"+File.separator+"SRTM41_N00E100_dem.tif";
	
	public DEMImpl() {
		try {
			File f = new File(filepath);
//			System.out.println(f.getAbsolutePath());
			this.reader = new GeoTiffReader(f);
			this.o1 = ((GridCoverage2D)reader.read(null));
			this.crs1 = o1.getCoordinateReferenceSystem();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public double getHeight(double lon, double lat) {
		try {
			DirectPosition dp1 = new DirectPosition2D(crs1, lon,lat);
			double ht = -999;
			ht = (double)o1.evaluate(dp1, values)[0];
			return ht;
		} catch (Exception e) {
			e.printStackTrace();
			return -999;
		}
	}

	public static void main(String[] args) {
		DEMImpl dem = new DEMImpl();
		double height = dem.getHeight(103.75, 1.33);
		System.out.println(height);
	}
	
}
