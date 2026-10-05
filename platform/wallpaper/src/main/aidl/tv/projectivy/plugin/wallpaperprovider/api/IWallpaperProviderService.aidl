// Projectivy wallpaper plugin API (https://github.com/spocky/projectivy-plugin-wallpaper-provider,
// Apache-2.0). The package and method order must stay as they are: they are the binder contract.
package tv.projectivy.plugin.wallpaperprovider.api;

import tv.projectivy.plugin.wallpaperprovider.api.Event;
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper;

interface IWallpaperProviderService {
    List<Wallpaper> getWallpapers(in Event event);
    String getPreferences();
    void setPreferences(String params);
}
