package lasertag;

import java.util.ArrayList;
import java.util.List;

/** A fired laser: a polyline that may ricochet off walls. Purely visual once created. */
final class Beam {
    final int id, owner;
    double life = Rules.BEAM_LIFE;
    final List<double[]> points = new ArrayList<>();

    Beam(int id, int owner) {
        this.id = id;
        this.owner = owner;
    }

    void add(double x, double y) {
        points.add(new double[] {x, y});
    }
}
