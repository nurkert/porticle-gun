package eu.nurkert.porticlegun.handlers.portals;

import eu.nurkert.porticlegun.portals.Portal;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;

public class TeleportationHandler implements Listener {

    @EventHandler
    public void on(PlayerMoveEvent event) {
        // check if the player moved to a different block
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            Player player = event.getPlayer();
            ArrayList<Portal> portals = ActivePortalsHandler.getRelevantPortals(player);
            for (Portal portal : portals) {
                if (portal.isInPortal(event.getTo()) || (portal.getDirection().getY() == -1.0 && portal.isInPortal(event.getTo().clone().add(0, 1, 0)))) {
                    if(portal.getLinkedPortal() != null) {
                        Vector preTeleportVelocity = player.getVelocity().clone();
                        Vector preTeleportLook = player.getLocation().getDirection().clone();

                        Portal linkedPortal = portal.getLinkedPortal();

                        PortalBasis sourceBasis = createPortalBasis(portal.getDirection());
                        PortalBasis destinationBasis = createPortalBasis(linkedPortal.getDirection());

                        Vector transformedVelocity = transformVector(preTeleportVelocity, sourceBasis, destinationBasis);
                        Vector transformedLook = transformVector(preTeleportLook, sourceBasis, destinationBasis).normalize();

                        Location destination = linkedPortal.getLocation().clone().add(0.5, linkedPortal.getDirection().getY() == -1.0 ? -1 : 0, 0.5);
                        destination.setDirection(transformedLook);

                        player.teleport(destination);
                        player.setVelocity(transformedVelocity);
                        break;
                    }
                }
            }
        }

    }

    static PortalBasis createPortalBasis(Vector direction) {
        Vector forward = direction.clone();
        if (forward.lengthSquared() == 0) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();

        Vector auxiliaryAxis = Math.abs(forward.getY()) > 0.999 ? new Vector(0, 0, 1) : new Vector(0, 1, 0);
        Vector right = forward.clone().crossProduct(auxiliaryAxis);
        if (right.lengthSquared() == 0) {
            auxiliaryAxis = new Vector(1, 0, 0);
            right = forward.clone().crossProduct(auxiliaryAxis);
        }
        right.normalize();

        Vector up = right.clone().crossProduct(forward).normalize();

        return new PortalBasis(forward, up, right);
    }

    static Vector transformVector(Vector vector, PortalBasis source, PortalBasis destination) {
        double forwardComponent = vector.dot(source.forward);
        double upComponent = vector.dot(source.up);
        double rightComponent = vector.dot(source.right);

        Vector transformed = destination.forward.clone().multiply(forwardComponent);
        transformed.add(destination.up.clone().multiply(upComponent));
        transformed.add(destination.right.clone().multiply(rightComponent));
        return transformed;
    }

    static class PortalBasis {
        final Vector forward;
        final Vector up;
        final Vector right;

        PortalBasis(Vector forward, Vector up, Vector right) {
            this.forward = forward;
            this.up = up;
            this.right = right;
        }
    }

    double[][] expected = {
            new double[]{26d, 12d, 43d, 12d},
            new double[]{17d, 10d, 30d, 17d},
            new double[]{36d, 16d, 59d, 14d}
    };

    /**
     * Multiplies two matrices.
     *
     * @param firstMatrix  the first matrix
     * @param secondMatrix the second matrix
     * @return the result of the multiplication
     */
    double[][] multiplyMatrices(double[][] firstMatrix, double[][] secondMatrix) {
        double[][] result = new double[firstMatrix.length][secondMatrix[0].length];

        for (int row = 0; row < result.length; row++) {
            for (int col = 0; col < result[row].length; col++) {
                result[row][col] = multiplyMatricesCell(firstMatrix, secondMatrix, row, col);
            }
        }

        return result;
    }

    /**
     * Multiplies a cell of the result matrix.
     *
     * @param firstMatrix  the first matrix
     * @param secondMatrix the second matrix
     * @param row          the row of the cell
     * @param col          the column of the cell
     * @return the result of the multiplication
     */
    double multiplyMatricesCell(double[][] firstMatrix, double[][] secondMatrix, int row, int col) {
        double cell = 0;
        for (int i = 0; i < secondMatrix.length; i++) {
            cell += firstMatrix[row][i] * secondMatrix[i][col];
        }
        return cell;
    }
}
