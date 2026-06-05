export interface ProfilePreferences {
  defaultAiMode: string
  notificationRealtime: boolean
  compactMode: boolean
  themeMode: string
}

export const PROFILE_PREFERENCES_KEY = 'teamflow_profile_preferences'

export const DEFAULT_PROFILE_PREFERENCES: ProfilePreferences = {
  defaultAiMode: 'CHAT',
  notificationRealtime: true,
  compactMode: false,
  themeMode: 'SYSTEM'
}

export function loadProfilePreferences(): ProfilePreferences {
  const raw = localStorage.getItem(PROFILE_PREFERENCES_KEY)
  if (!raw) {
    return { ...DEFAULT_PROFILE_PREFERENCES }
  }
  try {
    return {
      ...DEFAULT_PROFILE_PREFERENCES,
      ...(JSON.parse(raw) as Partial<ProfilePreferences>)
    }
  } catch {
    return { ...DEFAULT_PROFILE_PREFERENCES }
  }
}

export function saveProfilePreferences(preferences: ProfilePreferences) {
  localStorage.setItem(PROFILE_PREFERENCES_KEY, JSON.stringify(preferences))
  applyProfilePreferences(preferences)
}

export function applyProfilePreferences(preferences = loadProfilePreferences()) {
  document.documentElement.dataset.density = preferences.compactMode ? 'compact' : 'comfortable'
  document.documentElement.dataset.themeMode = preferences.themeMode.toLowerCase()
}
