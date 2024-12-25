aniyomi = {}
function aniyomi.show_text(text)
    mp.set_property("user-data/aniyomi/show_text", text)
end
function aniyomi.hide_ui()
    mp.set_property("user-data/aniyomi/toggle_ui", "hide")
end
function aniyomi.show_ui()
    mp.set_property("user-data/aniyomi/toggle_ui", "show")
end
function aniyomi.toggle_ui()
    mp.set_property("user-data/aniyomi/toggle_ui", "toggle")
end
function aniyomi.show_subtitle_settings()
   mp.set_property("user-data/aniyomi/show_panel", "subtitle_settings")
end
function aniyomi.show_subtitle_delay()
    mp.set_property("user-data/aniyomi/show_panel", "subtitle_delay")
end
function aniyomi.show_audio_delay()
    mp.set_property("user-data/aniyomi/show_panel", "audio_delay")
end
function aniyomi.show_video_filters()
    mp.set_property("user-data/aniyomi/show_panel", "video_filters")
end
function aniyomi.set_button_title(text)
   mp.set_property("user-data/aniyomi/set_button_title", text)
end
function aniyomi.reset_button_title(text)
    mp.set_property("user-data/aniyomi/reset_button_title", "unused")
end
function aniyomi.previous_episode()
   mp.set_property("user-data/aniyomi/switch_episode", "p")
end
function aniyomi.next_episode()
    mp.set_property("user-data/aniyomi/switch_episode", "n")
end
function aniyomi.int_picker(title, name_format, start, stop, step, property)
   mp.set_property("user-data/aniyomi/launch_int_picker", title .. "|" .. name_format ..  "|" .. start .. "|" .. stop .. "|" .. step .. "|" .. property)
end
return aniyomi
