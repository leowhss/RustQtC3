package dsta.prop;

public interface WorldCover {
	/*
	 * Returns the cover type at the lon(degrees), lat(degrees) location. Refer to CoverType for the list of integer values.
	 */
	public int getCoverTypeCache(double lon, double lat);
}
