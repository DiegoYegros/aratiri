package com.aratiri.admin.application.port.out;

import com.aratiri.admin.domain.NodeSettings;

public interface NodeSettingsPort {

    NodeSettings loadSettings();

    NodeSettings updateAutoManagePeers(boolean enabled);
}
