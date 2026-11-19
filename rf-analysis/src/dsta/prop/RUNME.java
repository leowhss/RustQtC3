package dsta.prop;

public class RUNME {

	public static void main(String[] args) {
		BuildingRasterImpl br = new BuildingRasterImpl();
		DEMImpl dem = new DEMImpl();
		WorldCoverImpl wc = new WorldCoverImpl();
		
		P1546Model p = new P1546Model(br, wc, dem);
		
		/*
		 * @param frequency     frequency in MHz
		 * @param txHeight      transmitter antenna mast height in metres (length of antenna)
		 * @param rcvHeight     receiver antenna mast height in metres (length of antenna)
		 * @param tx_lon        tx point longitude
		 * @param tx_lat        tx point latitude
		 * @param rx_lon        rx point longitude
		 * @param rx_lat        rx point latitude
		 * @param percentage_time       Required percentage time 1 % - 50 %
		 * @return              propagation loss in dB
		 */
		double frequency = 800;
		double txHeight = 3;
		double rxHeight = 3;
		double tx_lon = 103.75;
		double tx_lat = 1.33;
		double rx_lon = 103.74;
		double rx_lat = 1.333;
		double percentage = 1;
		
		double loss = p.getPropagationLoss(frequency, txHeight, rxHeight, tx_lon, tx_lat, rx_lon, rx_lat, percentage);
		System.out.println(loss);  //120.85375307807792
	}

}
