package com.stockflow.ai;

interface AiProviderGateway {

    AiModels.ProviderResult execute(AiModels.ProviderRequest request);

    AiModels.ProviderResult synthesize(AiModels.BusinessSynthesisRequest request);

    AiModels.ProviderResult selectRestock(AiModels.RestockSelectionRequest request);

    String providerName();

    String configuredModel();
}
