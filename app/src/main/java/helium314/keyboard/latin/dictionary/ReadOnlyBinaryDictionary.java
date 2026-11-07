/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.dictionary;

import com.android.inputmethod.latin.BinaryDictionary;

import helium314.keyboard.latin.NgramContext;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.common.ComposedData;
import helium314.keyboard.latin.makedict.WordProperty;
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class provides binary dictionary reading operations with locking. An instance of this class
 * can be used by multiple threads. Note that different session IDs must be used when multiple
 * threads get suggestions using this class.
 */
public final class ReadOnlyBinaryDictionary extends Dictionary {
    /**
     * A lock for accessing binary dictionary. Only closing binary dictionary is the operation
     * that change the state of dictionary.
     */
    private final ReentrantReadWriteLock mLock = new ReentrantReadWriteLock();

    private final BinaryDictionary mBinaryDictionary;

    public ReadOnlyBinaryDictionary(final String filename, final long offset, final long length,
            final boolean useFullEditDistance, final Locale locale, final String dictType) {
        super(dictType, locale);
        mBinaryDictionary = new BinaryDictionary(filename, offset, length, useFullEditDistance,
                locale, dictType, false /* isUpdatable */);
    }

    /** Enumerate only public dictionary entries, under the same close/read lock as suggestions.
     * Worker-only; maximum 15k retained words, 250k visited entries and ten seconds per source. */
    @Override
    public java.util.List<helium314.keyboard.latin.ai.local.LocalGestureDecoder.Word> getGestureWords(int requestedLimit) {
        final int limit=Math.min(15000,Math.max(0,requestedLimit));
        if(limit==0||TYPE_EMOJI.equals(mDictType))return java.util.Collections.emptyList();
        java.util.PriorityQueue<helium314.keyboard.latin.ai.local.LocalGestureDecoder.Word> best=
                new java.util.PriorityQueue<>(java.util.Comparator.comparingInt(word->word.frequency));
        int token=0,visited=0;
        long deadline=android.os.SystemClock.elapsedRealtime()+10000;
        do {
            if(Thread.currentThread().isInterrupted()||android.os.SystemClock.elapsedRealtime()>deadline||mLock.hasQueuedThreads())break;
            // Never retain a read lock across enumeration: close/language switches must stay fast.
            if(!mLock.readLock().tryLock())break;
            BinaryDictionary.GetNextWordPropertyResult next;
            try {
                if(!mBinaryDictionary.isValidDictionary())break;
                next=mBinaryDictionary.getNextWordProperty(token);
            } finally {mLock.readLock().unlock();}
            WordProperty property=next.mWordProperty;
            if(property==null)break;
            String text=property.mWord;
            if(!property.mIsNotAWord&&!property.mIsPossiblyOffensive&&property.getProbability()>2
                    &&text!=null&&text.matches("[\\p{L}\\p{M}]{2,32}")) {
                helium314.keyboard.latin.ai.local.LocalGestureDecoder.Word candidate=
                        new helium314.keyboard.latin.ai.local.LocalGestureDecoder.Word(text,property.getProbability());
                if(best.size()<limit)best.add(candidate);
                else if(candidate.frequency>best.peek().frequency){best.poll();best.add(candidate);}
            }
            token=next.mNextToken;
        }while(token!=0&&++visited<250000);
        return new java.util.ArrayList<>(best);
    }

    public boolean isValidDictionary() {
        return mBinaryDictionary.isValidDictionary();
    }

    @Override
    public ArrayList<SuggestedWordInfo> getSuggestions(final ComposedData composedData,
            final NgramContext ngramContext, final long proximityInfoHandle,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final int sessionId, final float weightForLocale,
            final float[] inOutWeightOfLangModelVsSpatialModel) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getSuggestions(composedData, ngramContext,
                        proximityInfoHandle, settingsValuesForSuggestion, sessionId,
                        weightForLocale, inOutWeightOfLangModelVsSpatialModel);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return null;
    }

    @Override
    public boolean isInDictionary(final String word) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.isInDictionary(word);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return false;
    }

    @Override
    public boolean shouldAutoCommit(final SuggestedWordInfo candidate) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.shouldAutoCommit(candidate);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return false;
    }

    @Override
    public int getFrequency(final String word) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getFrequency(word);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return NOT_A_PROBABILITY;
    }

    @Override
    public int getMaxFrequencyOfExactMatches(final String word) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getMaxFrequencyOfExactMatches(word);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return NOT_A_PROBABILITY;
    }

    @Override
    public WordProperty getWordProperty(String word, boolean isBeginningOfSentence) {
        if (mLock.readLock().tryLock()) {
            try {
                return mBinaryDictionary.getWordProperty(word, isBeginningOfSentence);
            } finally {
                mLock.readLock().unlock();
            }
        }
        return null;
    }

    @Override
    public void close() {
        mLock.writeLock().lock();
        try {
            mBinaryDictionary.close();
        } finally {
            mLock.writeLock().unlock();
        }
    }

    public String getHash() {
        return mBinaryDictionary.getHash();
    }
}
