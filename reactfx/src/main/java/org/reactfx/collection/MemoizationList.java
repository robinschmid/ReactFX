package org.reactfx.collection;

import java.util.List;
import java.util.Optional;

import javafx.collections.ObservableList;
import javafx.scene.control.IndexRange;

import org.reactfx.Subscription;
import org.reactfx.util.SparseList;

public interface MemoizationList<E> extends LiveList<E> {
    LiveList<E> memoizedItems();
    boolean isMemoized(int index);
    Optional<E> getIfMemoized(int index);
    int getMemoizedCountBefore(int position);
    int getMemoizedCountAfter(int position);
    void forget(int from, int to);
    int indexOfMemoizedItem(int index);
    IndexRange getMemoizedItemsRange();
}

class MemoizationListImpl<E>
extends LiveListBase<E>
implements MemoizationList<E>, ReadOnlyLiveListImpl<E> {

    private class MemoizedView
    extends LiveListBase<E>
    implements ReadOnlyLiveListImpl<E> {

        @Override
        protected Subscription observeInputs() {
            return MemoizationListImpl.this.pin();
        }

        @Override
        public E get(int index) {
            return sparseList.getPresent(index);
        }

        @Override
        public int size() {
            return sparseList.getPresentCount();
        }

        private void prepareNotifications(QuasiListChange<? extends E> event) {
            enqueueNotifications(event);
        }

        private void publishNotifications() {
            notifyObservers();
        }
    }

    private final SparseList<E> sparseList = new SparseList<>();
    private final MemoizedView memoizedItems = new MemoizedView();
    private final ObservableList<E> source;

    MemoizationListImpl(ObservableList<E> source) {
        this.source = source;
        sparseList.insertVoid(0, source.size());
    }

    @Override
    protected Subscription observeInputs() {
        sparseList.insertVoid(0, source.size());
        return LiveList.<E>observeQuasiChanges(source, this::sourceChanged)
            .and(sparseList::clear);
    }

    private void sourceChanged(QuasiListChange<? extends E> qc) {
        ListChangeAccumulator<E> acc = new ListChangeAccumulator<>();
        for(QuasiListModification<? extends E> mod: qc) {
            int from = mod.getFrom();
            int removedSize = mod.getRemovedSize();
            int memoFrom = sparseList.getPresentCountBefore(from);
            List<E> memoRemoved = sparseList.collect(from, from + removedSize);
            sparseList.spliceByVoid(from, from + removedSize, mod.getAddedSize());
            acc.add(new QuasiListModificationImpl<>(memoFrom, memoRemoved, 0));
        }
        memoizedItems.prepareNotifications(acc.fetch());
        notifyObservers(qc);
        memoizedItems.publishNotifications();
    }

    @Override
    public E get(int index) {
        if(sparseList.isPresent(index)) {
            return sparseList.getOrThrow(index);
        } else {
            E elem = source.get(index);
            sparseList.set(index, elem);
            memoizedItems.fireElemInsertion(
                    sparseList.getPresentCountBefore(index));
            return elem;
        }
    }

    @Override
    public int size() {
        return source.size();
    }

    @Override
    public LiveList<E> memoizedItems() {
        return memoizedItems;
    }

    @Override
    public boolean isMemoized(int index) {
        return sparseList.isPresent(index);
    }

    @Override
    public Optional<E> getIfMemoized(int index) {
        return sparseList.get(index);
    }

    @Override
    public int getMemoizedCountBefore(int position) {
        return sparseList.getPresentCountBefore(position);
    }

    @Override
    public int getMemoizedCountAfter(int position) {
        return sparseList.getPresentCountAfter(position);
    }

    @Override
    public void forget(int from, int to) {
        sparseList.spliceByVoid(from, to, to - from);
    }

    @Override
    public int indexOfMemoizedItem(int index) {
        return sparseList.indexOfPresentItem(index);
    }

    @Override
    public IndexRange getMemoizedItemsRange() {
        return sparseList.getPresentItemsRange();
    }
}