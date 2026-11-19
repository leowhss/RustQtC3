package dsta.prop;

public interface BuildingRaster {
	/*
	 * Returns the height(metres) of the building at the lon(degrees), lat(degrees) location. If the location does not have building, the return value should be 0
	 */
	public double getHeight(double lon, double lat);
}
