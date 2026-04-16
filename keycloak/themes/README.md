# Esquire Explorer Keycloak Theme

Custom Keycloak login theme that matches the visual design of the Esquire Explorer app-explorer component pre-login state.

## Theme Structure

```
C:\MyProjects\esquire\services\keycloak\themes\esquire-explorer\login\
├── theme.properties              # parent=keycloak; styles; locales
├── template.ftl                  # Master layout: toolbars, alert block, <#nested>
├── login.ftl                     # Credentials form
├── login-reset-password.ftl      # Forgot-password form
├── login-update-password.ftl     # Forced password change
├── login-otp.ftl                 # OTP entry
├── login-config-totp.ftl         # TOTP setup
├── login-page-expired.ftl        # Session-expired page
├── logout-confirm.ftl            # Logout confirmation
├── error.ftl                     # Generic error page
├── info.ftl                      # Generic info/redirect page
├── messages/
│   └── messages_en.properties   # Text labels and messages
└── resources/
    ├── css/
    │   └── styles.css           # All styling (Roboto, Material-like)
    └── img/
        ├── main.ico             # Application logo (48x48)
        └── unknown.ico          # Default user icon (24x24)
```

### Template inheritance note

`theme.properties` sets `parent=keycloak`. Any template not listed above falls through to the
Keycloak base theme. The base theme uses a `; section` loop-variable pattern in its macro
calls (`<@layout.registrationLayout; section>`) which is incompatible with the Esquire
`template.ftl` (uses flat `<#nested>`). Any unoverridden base template that uses that pattern
will render a blank content area. Override every template that can appear in the login flow.

## Deployment with Docker Compose

### 1. Theme Files Already Deployed

The theme files are located in:
```
C:\MyProjects\esquire\services\keycloak\themes\esquire-explorer\
```

The `compose.yaml` has been configured to mount this directory into the Keycloak container:
```yaml
volumes:
  - ./themes:/opt/keycloak/themes
```

### 2. Start Keycloak

From `C:\MyProjects\esquire\services\keycloak\`:

**Windows:**
```cmd
docker-compose-up.bat
```

**Command Line:**
```bash
docker compose up -d
```

### 3. Configure Realm to Use Theme

1. Open Keycloak Admin Console: http://localhost:8080
2. Login with admin credentials (username: `admin`, password: `q`)
3. Select your realm: `esquire`
4. Navigate to **Realm Settings** → **Themes** tab
5. Set **Login Theme** dropdown to `esquire-explorer`
6. Click **Save**

### 4. Test the Theme

1. Navigate to your application login URL
2. The login page should match the Esquire Explorer pre-login appearance:
   - Top toolbar with logo and "Esquire Tree Explorer" title
   - "Disconnected" user indicator in name-bar
   - Centered login form with "Please login to access the explorer features." message
   - Bottom toolbar with copyright notice

## Visual Design Match

The theme replicates the following elements from `app-explorer.component`:

### Layout
- Grid layout: header (toolbar), main content, footer (toolbar)
- Top toolbar: logo (48x48), "Esquire Tree Explorer" title, user profile indicator
- Main area: centered pre-login message and login form
- Bottom toolbar: status text and right-aligned copyright

### Styling
- Background: `rgba(235, 235, 235, 0.5)` toolbar
- Font: Roboto, "Helvetica Neue", sans-serif
- Colors: Material Design color palette
- Primary button: Material indigo (`#3f51b5`)

### Components
- Name-bar with rounded border (8px radius)
- Info text at 9pt font size
- Material Design login form with styled input fields

## Source Files

Theme source is maintained in the frontend project:
```
C:\MyProjects\esquire\explorer\frontend\keycloak-theme\esquire-explorer\
```

After making changes to the source, copy to the deployment location:
```bash
# From frontend/keycloak-theme to services/keycloak/themes
cp -r C:/MyProjects/esquire/explorer/frontend/keycloak-theme/esquire-explorer/* \
      C:/MyProjects/esquire/services/keycloak/themes/esquire-explorer/
```

## Customization

### Change Colors

Edit `resources/css/styles.css`:

```css
/* Primary button color */
.btn-login {
    background-color: #3f51b5;  /* Change this */
}

/* Toolbar background */
.mat-toolbar {
    background: rgba(235, 235, 235, 0.5);  /* Change this */
}
```

After changes, restart Keycloak:
```bash
docker compose restart
```

### Modify Layout

Edit `login.ftl` to adjust HTML structure while maintaining the visual match.

### Add Background Image

Add to `resources/css/styles.css`:

```css
.prelogin-container {
    background-image: url('../img/background.jpg');
    background-size: cover;
    background-position: center;
}
```

Then add your image to `resources/img/background.jpg`.

## Troubleshooting

### Theme Not Appearing

1. Verify volume mount in `compose.yaml`:
   ```bash
   docker compose config | grep -A 5 volumes
   ```

2. Check theme files inside container:
   ```bash
   docker exec keycloak ls -la /opt/keycloak/themes/esquire-explorer/login/
   ```

3. Restart Keycloak container:
   ```bash
   docker compose restart
   ```

4. Clear browser cache: Ctrl+Shift+R (Windows/Linux) or Cmd+Shift+R (Mac)

### Images Not Loading

1. Verify images are copied:
   ```bash
   ls C:/MyProjects/esquire/services/keycloak/themes/esquire-explorer/login/resources/img/
   ```

2. Check browser DevTools → Network tab for 404 errors

3. Verify image paths in `login.ftl` match the resources structure

### Styling Issues

1. Verify `styles.css` is loaded in browser DevTools → Network tab

2. Check for CSS errors in browser console

3. Force theme cache clear by restarting container:
   ```bash
   docker compose down
   docker compose up -d
   ```

## Container Logs

View Keycloak logs for theme loading errors:
```bash
docker compose logs -f keycloak
```

## Maintenance

When updating the Angular app-explorer component styles:

1. Review changes in:
   ```
   C:\MyProjects\esquire\explorer\frontend\src\explorer\flatTree\app-explorer.component.scss
   ```

2. Update source theme CSS:
   ```
   C:\MyProjects\esquire\explorer\frontend\keycloak-theme\esquire-explorer\login\resources\css\styles.css
   ```

3. Copy to deployment location:
   ```bash
   cp C:/MyProjects/esquire/explorer/frontend/keycloak-theme/esquire-explorer/login/resources/css/styles.css \
      C:/MyProjects/esquire/services/keycloak/themes/esquire-explorer/login/resources/css/
   ```

4. Restart Keycloak:
   ```bash
   cd C:/MyProjects/esquire/services/keycloak
   docker compose restart
   ```

## Directory Structure Overview

```
C:\MyProjects\esquire\
├── explorer\frontend\
│   └── keycloak-theme\          # Source (development)
│       └── esquire-explorer\
└── services\
    ├── keycloak\
    │   ├── compose.yaml         # Docker Compose config
    │   └── themes\              # Deployed theme (docker volume mount)
    │       └── esquire-explorer\
    └── compose\
        └── compose.yaml         # Main services compose
```

## Support

For issues or questions:
- Email: mir0n.the.programmer@gmail.com
- Copyright © 2001, 2026 mir0n&co www.mir0n.me
