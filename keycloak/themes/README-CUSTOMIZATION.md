# Esquire Explorer Keycloak Theme - Customization Guide

## Theme Files Location

**Compose (deployed):**
```
C:\MyProjects\esquire\services\compose\themes\esquire-explorer\login\
```

**Keycloak (standalone):**
```
C:\MyProjects\esquire\services\keycloak\themes\esquire-explorer\login\
```

**Frontend (source):**
```
C:\MyProjects\esquire\explorer\frontend\keycloak-theme\esquire-explorer\login\
```

## File Structure

```
esquire-explorer/login/
├── theme.properties          # Theme configuration
├── template.ftl             # Main HTML layout (header, footer, structure)
├── login.ftl                # Login form content
├── messages/
│   └── messages_en.properties  # Text labels and messages
└── resources/
    ├── css/
    │   └── styles.css       # All styling
    └── img/
        ├── main.ico         # Logo (48x48)
        └── unknown.ico      # User icon (24x24)
```

## Common Customizations

### 1. Update Colors

Edit `resources/css/styles.css`:

```css
/* Change button color */
.btn-login {
    background-color: #3f51b5;  /* Material indigo */
}

/* Change toolbar background */
.mat-toolbar {
    background: rgba(235, 235, 235, 0.5);
}

/* Change login form background */
.login-form-wrapper {
    background: rgba(255, 255, 255, 0.95);
}
```

### 2. Update Text Labels

Edit `messages/messages_en.properties`:

```properties
loginTitle=Sign in to {0}
username=Username
password=Password
doLogIn=Sign In
doForgotPassword=Forgot Password?
rememberMe=Remember me
```

### 3. Adjust Layout

Edit `template.ftl` - main structure:
- Lines 20-27: Top toolbar (logo, title, user indicator)
- Lines 30-42: Main content area (message, login form)
- Lines 46-49: Bottom toolbar (realm name, copyright)

Edit `login.ftl` - form fields:
- Lines 4-12: Username field
- Lines 14-18: Password field
- Lines 32: Sign In button

### 4. Change Images

Replace files in `resources/img/`:
- `main.ico` - Logo (48x48 pixels)
- `unknown.ico` - User icon (24x24 pixels)

## Deployment Workflow

After making changes:

### Option 1: Quick CSS/Image Updates (no rebuild needed)
```bash
# Copy changes to compose themes
cp <your-changes> C:/MyProjects/esquire/services/compose/themes/esquire-explorer/login/

# Restart Keycloak
cd C:/MyProjects/esquire/services/compose
docker compose restart keycloak
```

### Option 2: Template Changes (requires rebuild)
```bash
# 1. Update theme files in compose/themes/
# 2. Rebuild Keycloak image
cd C:/MyProjects/esquire/services/compose
docker compose down
docker compose build keycloak
docker compose up -d
```

### Option 3: Full Sync (keep all 3 locations in sync)
```bash
# Sync from compose to keycloak standalone
cp -r C:/MyProjects/esquire/services/compose/themes/esquire-explorer/* \
      C:/MyProjects/esquire/services/keycloak/themes/esquire-explorer/

# Sync from compose to frontend source
cp -r C:/MyProjects/esquire/services/compose/themes/esquire-explorer/* \
      C:/MyProjects/esquire/explorer/frontend/keycloak-theme/esquire-explorer/
```

## CSS Classes Reference

### Main Containers
- `.app-explorer` - Root container (grid layout)
- `.mat-toolbar` - Top and bottom toolbars
- `.prelogin-container` - Main content area
- `.login-form-wrapper` - Form container

### Form Elements
- `.form-group` - Form field wrapper
- `.control-label` - Field labels
- `.form-control` - Input fields
- `.btn-login` - Sign In button
- `.checkbox` - Remember Me checkbox

### Other Elements
- `.name-bar` - User status indicator (top right)
- `.info-text` - Helper text
- `.alert` - Error/success messages

## Tips

1. **Browser Cache**: Always hard refresh (Ctrl+Shift+R) after changes
2. **Template Errors**: Check `docker logs esq-keycloak` for FreeMarker errors
3. **CSS**: Keep specificity low to avoid conflicts
4. **Images**: Use relative paths `${url.resourcesPath}/img/filename`
5. **Messages**: Use `${msg("key")}` for internationalization

## Troubleshooting

**Theme not updating?**
- Clear browser cache (Ctrl+Shift+Delete)
- Restart Keycloak: `docker compose restart keycloak`
- Check Admin Console: Realm Settings → Themes → Login Theme = `esquire-explorer`

**Blank page or errors?**
- Check FreeMarker syntax in .ftl files
- Verify all `${variables}` are properly closed
- Check `docker logs esq-keycloak` for template errors

**Client override issue?**
- Admin Console → Clients → esq-angular → Settings
- Ensure "Login Theme" is blank (uses realm default)

## Key Files to Remember

**Don't forget the client override was the issue!**
- `import/esquire.json` line ~843: Removed `"login_theme" : "keycloak"`
- This allows the realm-level theme to be used

## Support

- Theme documentation: C:\MyProjects\esquire\services\compose\themes\README.md
- Keycloak docs: https://www.keycloak.org/docs/latest/server_development/#_themes
