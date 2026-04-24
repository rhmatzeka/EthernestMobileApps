package id.rahmat.projekakhir.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

import id.rahmat.projekakhir.databinding.ItemTokenBinding;

public class TokenAdapter extends RecyclerView.Adapter<TokenAdapter.TokenViewHolder> {

    private final List<TokenItem> items;

    public TokenAdapter(List<TokenItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public TokenViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTokenBinding binding = ItemTokenBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new TokenViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TokenViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TokenViewHolder extends RecyclerView.ViewHolder {

        private final ItemTokenBinding binding;

        TokenViewHolder(ItemTokenBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TokenItem item) {
            binding.textTokenSymbol.setText(item.getSymbol());
            binding.textTokenBadge.setText(item.getBadge());
            binding.textTokenName.setText(item.getName());
            binding.textTokenBalance.setText(item.getBalance());
            binding.textTokenFiat.setText(item.getFiatValue());

            if (item.getImageResId() != 0) {
                Glide.with(binding.imageToken.getContext()).clear(binding.imageToken);
                binding.imageToken.setImageResource(item.getImageResId());
            } else {
                Glide.with(binding.imageToken.getContext())
                        .load(item.getImageUrl())
                        .into(binding.imageToken);
            }
        }
    }
}
