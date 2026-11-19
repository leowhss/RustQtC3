package dsta.prop;

public interface DEM {
	/*
	 * Returns the terrain height(metres) referenced to mean sea level at lon(degrees), lat(degrees) location.
	 */
	public double getHeight(double lon, double lat);
}
