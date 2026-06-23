package com.iflytek.skillhub.notification.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.notification.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    public record PreferenceView(NotificationCategory category, NotificationChannel channel, boolean enabled) {}
    public record PreferenceCommand(NotificationCategory category, NotificationChannel channel, boolean enabled) {}

    public boolean isEnabled(String userId, NotificationCategory category, NotificationChannel channel) {
        return preferenceRepository.findByUserIdAndCategoryAndChannel(userId, category, channel)
                .map(NotificationPreference::isEnabled)
                .orElse(defaultEnabled(category, channel));
    }

    @Transactional(readOnly = true)
    public List<PreferenceView> getPreferences(String userId) {
        Map<String, Boolean> saved = preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        p -> preferenceKey(p.getCategory(), p.getChannel()),
                        NotificationPreference::isEnabled));
        List<PreferenceView> preferences = Arrays.stream(NotificationCategory.values())
                .map(cat -> new PreferenceView(
                        cat,
                        NotificationChannel.IN_APP,
                        saved.getOrDefault(preferenceKey(cat, NotificationChannel.IN_APP), defaultEnabled(cat, NotificationChannel.IN_APP))))
                .collect(Collectors.toList());
        preferences.add(new PreferenceView(
                NotificationCategory.WEEKLY_SKILL,
                NotificationChannel.FEISHU,
                saved.getOrDefault(preferenceKey(NotificationCategory.WEEKLY_SKILL, NotificationChannel.FEISHU), false)));
        preferences.add(new PreferenceView(
                NotificationCategory.WEEKLY_SKILL,
                NotificationChannel.DINGTALK,
                saved.getOrDefault(preferenceKey(NotificationCategory.WEEKLY_SKILL, NotificationChannel.DINGTALK), false)));
        return preferences;
    }

    @Transactional
    public void updatePreference(String userId, NotificationCategory category,
                                  NotificationChannel channel, boolean enabled) {
        if (!isSupportedPreference(category, channel)) {
            throw new DomainBadRequestException("error.notification.preference.channel.unsupported", channel.name());
        }
        NotificationPreference pref = preferenceRepository
                .findByUserIdAndCategoryAndChannel(userId, category, channel)
                .orElse(null);
        if (pref == null) {
            pref = new NotificationPreference(userId, category, channel, enabled);
        } else {
            pref.setEnabled(enabled);
        }
        preferenceRepository.save(pref);
    }

    @Transactional
    public void updatePreferences(String userId, List<PreferenceCommand> commands) {
        if (commands == null) {
            throw new DomainBadRequestException("error.notification.preference.request.invalid");
        }
        long distinctCount = commands.stream()
                .map(command -> command.category().name() + ":" + command.channel().name())
                .distinct()
                .count();
        if (distinctCount != commands.size()) {
            throw new DomainBadRequestException("error.notification.preference.duplicate");
        }
        for (PreferenceCommand command : commands) {
            updatePreference(userId, command.category(), command.channel(), command.enabled());
        }
    }

    private boolean defaultEnabled(NotificationCategory category, NotificationChannel channel) {
        return channel == NotificationChannel.IN_APP && category != NotificationCategory.WEEKLY_SKILL;
    }

    private boolean isSupportedPreference(NotificationCategory category, NotificationChannel channel) {
        if (channel == NotificationChannel.IN_APP) {
            return true;
        }
        return category == NotificationCategory.WEEKLY_SKILL
                && (channel == NotificationChannel.FEISHU || channel == NotificationChannel.DINGTALK);
    }

    private String preferenceKey(NotificationCategory category, NotificationChannel channel) {
        return category.name() + ":" + channel.name();
    }
}
