/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tv.sample

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.datadog.android.Datadog
import com.datadog.android.ktx.rum.asRumResource
import com.datadog.android.tv.sample.model.Episode
import com.datadog.android.tv.sample.model.EpisodeList
import com.google.gson.Gson

class HomeActivity : AppCompatActivity() {

    val adapter = EpisodeRecyclerView.Adapter { episode, view ->
        onEpisodeSelected(episode)
    }

    lateinit var episodesRecyclerView: RecyclerView
    lateinit var episodeTitle: TextView
    lateinit var episodeDescription: TextView
    lateinit var episodeSpeakers: TextView
    lateinit var playView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tv)
        episodesRecyclerView = findViewById(R.id.episodes)
        episodeTitle = findViewById(R.id.episode_title)
        episodeDescription = findViewById(R.id.episode_description)
        episodeSpeakers = findViewById(R.id.episode_speakers)
        playView = findViewById(R.id.play)

        episodesRecyclerView.layoutManager = LinearLayoutManager(this)
        episodesRecyclerView.adapter = adapter

        Datadog._internalProxy()._telemetry.debug("Hello world")
    }

    override fun onResume() {
        super.onResume()
        Thread {
            val rawReader = resources.openRawResource(R.raw.episodes)
                .asRumResource("https://api.example.com/episodes")
                .reader()
            val episodeList = Gson().fromJson(rawReader, EpisodeList::class.java)
            runOnUiThread {
                adapter.updateData(episodeList.episodes.sortedBy { it.recordDate })
            }
        }.start()
    }

    private fun onEpisodeSelected(episode: Episode?) {
        episodeTitle.text = episode?.title.orEmpty()
        episodeDescription.text = episode?.description?.joinToString("\n").orEmpty()
        episodeSpeakers.text = episode?.speakers?.joinToString(", ").orEmpty()

        playView.isEnabled = !(episode?.video.isNullOrBlank())

        playView.setOnClickListener {
            if (episode != null) {
                val intent = Intent()
                intent.component = ComponentName(this, PlayerActivity::class.java)
                intent.data = Uri.parse(episode.video)
                startActivity(intent)
            }
        }
    }
}
