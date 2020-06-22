package com.leff.midi

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.leff.midi.event.MidiEvent
import com.leff.midi.event.NoteOff
import com.leff.midi.event.NoteOn
import com.leff.midi.event.meta.Tempo
import com.leff.midi.event.meta.TimeSignature
import com.leff.midi.util.MidiEventListener
import com.leff.midi.util.MidiProcessor
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        midi_output_1.movementMethod = ScrollingMovementMethod()
        midi_output_2.movementMethod = ScrollingMovementMethod()

        // 1. Create an example MIDI file
        val midi = midiFileFromScratch()

        // Manipulate it, just for the sake of example
        midiManipulation(midi)

        // 2. Create a MidiProcessor
        val processor = MidiProcessor(midi)

        // 3. Register listeners for the events you're interested in
        val ep = TextViewEventPrinter(midi_output_1)
        processor.registerEventListener(ep, Tempo::class.java)
        processor.registerEventListener(ep, NoteOn::class.java)

        // or listen for all events:
        val ep2 = TextViewEventPrinter(midi_output_2)
        processor.registerEventListener(ep2, MidiEvent::class.java)

        // 4. Start the processor
        processor.start()

        // Listeners will be triggered in real time with the MIDI events
        // And you can pause/resume with stop() and start()
        try {
            Thread.sleep(10 * 1000.toLong())
            processor.stop()
            Thread.sleep(10 * 1000.toLong())
            processor.start()
        } catch (e: Exception) {
        }
    }

    // Create a same MIDI file from scratch
    private fun midiFileFromScratch(): MidiFile {
        // 1. Create some MidiTracks
        val tempoTrack = MidiTrack()
        val noteTrack = MidiTrack()

        // 2. Add events to the tracks
        // 2a. Track 0 is typically the tempo map
        val ts = TimeSignature()
        ts.setTimeSignature(4, 4, TimeSignature.DEFAULT_METER, TimeSignature.DEFAULT_DIVISION)

        val t = Tempo()
        t.setBpm(228f)

        tempoTrack.insertEvent(ts)
        tempoTrack.insertEvent(t)

        // 2b. Track 1 will have some notes in it
        for (i in 0..79) {
            val channel = 0
            val pitch = 1 + i
            val velocity = 100
            val on = NoteOn((i * 480).toLong(), channel, pitch, velocity)
            val off = NoteOff((i * 480 + 120).toLong(), channel, pitch, 0)
            noteTrack.insertEvent(on)
            noteTrack.insertEvent(off)

            // There is also a utility function for notes that you should use
            // instead of the above.
            noteTrack.insertNote(channel, pitch + 2, velocity, i * 480.toLong(), 120)
        }

        // It's best not to manually insert EndOfTrack events; MidiTrack will
        // call closeTrack() on itself before writing itself to a file

        // 3. Return the MidiFile with the tracks we created
        val tracks: ArrayList<MidiTrack> = ArrayList<MidiTrack>()
        tracks.add(tempoTrack)
        tracks.add(noteTrack)

        return MidiFile(MidiFile.DEFAULT_RESOLUTION, tracks)
    }

    /**
     * Do some editing to a Midi file. This mutates the file object in memory.
     */
    private fun midiManipulation(mf: MidiFile) {
        // 1. Strip out anything but notes from track 1
        var T: MidiTrack = mf.tracks.get(1)

        // It's a bad idea to modify a set while iterating, so we'll collect
        // the events first, then remove them afterwards
        var it: Iterator<MidiEvent> = T.events.iterator()
        val eventsToRemove = ArrayList<MidiEvent>()

        while (it.hasNext()) {
            val E = it.next()
            if (E.javaClass != NoteOn::class.java && E.javaClass != NoteOff::class.java) {
                eventsToRemove.add(E)
            }
        }

        for (E in eventsToRemove) {
            T.removeEvent(E)
        }

        // 2. Completely remove track 2
        mf.removeTrack(2)

        // 3. Reduce the tempo by half
        T = mf.tracks[0]

        it = T.events.iterator()
        while (it.hasNext()) {
            val E = it.next()
            if (E.javaClass == Tempo::class.java) {
                val tempo = E as Tempo
                tempo.bpm = tempo.bpm / 2
            }
        }
    }

    private class TextViewEventPrinter(private val output: TextView) : MidiEventListener {

        private val handler = Handler(Looper.getMainLooper())

        override fun onStart(fromBeginning: Boolean) {
            if (fromBeginning) {
                handler.post { output.append("started!\n") }
            } else {
                handler.post { output.append("resumed!\n") }
            }
        }

        override fun onEvent(event: MidiEvent, ms: Long) {
            handler.post { output.append("received event: $event\n") }
        }

        override fun onStop(finished: Boolean) {
            if (finished) {
                handler.post { output.append("finished!\n") }
            } else {
                handler.post { output.append("paused\n") }
            }
        }
    }
}
