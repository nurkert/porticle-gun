package eu.nurkert.porticlegun.handlers.visualization.concrete;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public class RectangularVisualisation implements  PortalVisualization {
    @Override
    public Vector getParticleLocation(double radians, Location loc, Vector direction) {
        double x = 0, y = 0, z = 0;

        if (direction.getY() == 0.0) {
            // Horizontales Portal (stehend)
            double width = 0.9;
            double height = 1.9;
            double perimeter = 2 * (width + height);
            double progress = perimeter - (radians % (2 * Math.PI)) / (2 * Math.PI) * perimeter;

            // Position entlang der Kanten bestimmen
            if (progress < width) {
                // Untere Kante (von links nach rechts)
                x = progress + 0.05;
                y = 0.05;
            } else if (progress < width + height) {
                // Rechte Kante (von unten nach oben)
                x = width  + 0.05;
                y = progress - width + 0.05;
            } else if (progress < 2 * width + height) {
                // Obere Kante (von rechts nach links)
                x = width - (progress - (width + height))  + 0.05;
                y = height + 0.05;
            } else {
                // Linke Kante (von oben nach unten)
                x = 0.05;
                y = height - (progress - (2 * width + height)) + 0.05;
            }

            // Anpassung von loc wie im ursprünglichen Code
            loc = loc.clone().add(0.5 - 0.4 * direction.getX(), 0, 0.5 - 0.4 * direction.getZ());

            // Winkel für die Rotation basierend auf der Portalausrichtung berechnen
            double angle = Math.atan2(direction.getZ(), direction.getX()) - Math.PI / 2;
            double cosAngle = Math.cos(angle);
            double sinAngle = Math.sin(angle);

            // Rotation anwenden
            double rotatedX = x * cosAngle;
            double rotatedZ = x * sinAngle;

            return new Vector(
                    loc.getX() + rotatedX - 0.5 * direction.getZ(),
                    loc.getY() + y,
                    loc.getZ() + rotatedZ  + 0.5 * direction.getX()
            );

        } else if (direction.getY() < 0.0) {
            // Nach unten gerichtetes Portal
            double size = 1.0;
            double perimeter = 4 * size;
            double progress = (radians % (2 * Math.PI)) / (2 * Math.PI) * perimeter;

            if (progress < size) {
                x = progress;
                z = 0;
            } else if (progress < 2 * size) {
                x = size;
                z = progress - size;
            } else if (progress < 3 * size) {
                x = size - (progress - 2 * size);
                z = size;
            } else {
                x = 0;
                z = size - (progress - 3 * size);
            }

            // Anpassung von loc wie im ursprünglichen Code
            loc = loc.clone().add(0.5, 0.9, 0.5);

            return new Vector(
                    loc.getX() + x - 0.5,
                    loc.getY(),
                    loc.getZ() + z - 0.5
            );

        } else if (direction.getY() > 0.0) {
            // Nach oben gerichtetes Portal
            double size = 1.0;
            double perimeter = 4 * size;
            double progress = (radians % (2 * Math.PI)) / (2 * Math.PI) * perimeter;

            if (progress < size) {
                x = progress;
                z = 0;
            } else if (progress < 2 * size) {
                x = size;
                z = progress - size;
            } else if (progress < 3 * size) {
                x = size - (progress - 2 * size);
                z = size;
            } else {
                x = 0;
                z = size - (progress - 3 * size);
            }

            // Anpassung von loc wie im ursprünglichen Code
            loc = loc.clone().add(0.5, 0.1, 0.5);

            return new Vector(
                    loc.getX() + x - 0.5,
                    loc.getY(),
                    loc.getZ() + z - 0.5
            );
        }
        return null;
    }
}
