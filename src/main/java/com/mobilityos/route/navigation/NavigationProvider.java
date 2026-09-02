package com.mobilityos.route.navigation;

import com.mobilityos.route.navigation.dto.NavigationOption;
import com.mobilityos.route.navigation.dto.NavigationRequest;

import java.util.List;

public interface NavigationProvider {

    List<NavigationOption> calculateRoutes(
            NavigationRequest request
    );
}