package eu.nurkert.porticlegun.handlers.visualization;

import eu.nurkert.porticlegun.handlers.portals.OpenedPortalsHandler;
import eu.nurkert.porticlegun.portals.PortalColor;

public class GunColors {
    PortalColor primary;
    PortalColor secondary;

    public GunColors(PortalColor primary, PortalColor secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    public PortalColor getPrimary() {
        return primary;
    }

    public PortalColor getSecondary() {
        return secondary;
    }

    private void setPrimary(PortalColor primary, String gunID) {
        this.primary = primary;
        if(OpenedPortalsHandler.hasPrimaryPortal(gunID))
            OpenedPortalsHandler.getPrimaryPortal(gunID).setColor(primary);
    }

    private void setSecondary(PortalColor secondary, String gunID) {
        this.secondary = secondary;
        if(OpenedPortalsHandler.hasSecondaryPortal(gunID))
            OpenedPortalsHandler.getSecondaryPortal(gunID).setColor(secondary);
    }

    public void selectNextPrimary(String gunID) {
        setPrimary(primary.next(), gunID);
    }

    public void selectNextSecondary(String gunID) {
        setSecondary(secondary.next(), gunID);
    }

    public void selectPreviousPrimary(String gunID) {
        setPrimary(primary.previous(), gunID);
    }

    public void selectPreviousSecondary(String gunID) {
        setSecondary(secondary.previous(), gunID);
    }
}