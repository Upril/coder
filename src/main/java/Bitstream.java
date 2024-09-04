import org.encoder.utils.VideoObjectPlane;

import java.util.List;

public class Bitstream {
    private List<VideoObjectPlane> vops;

    public Bitstream(List<VideoObjectPlane> vops) {
        this.vops = vops;
    }

    public void writeToFile(String fileName) {
        // Implement writing the bitstream to a file
        System.out.println("Writing bitstream to file...");
    }
}
