package com.dogdog.nomat.domain.map.service;

import java.io.IOException;

public interface AudioExtractionCommandRunner {

    void extractYoutubeSegment(YoutubeAudioExtractionCommand command) throws IOException, InterruptedException;
}
